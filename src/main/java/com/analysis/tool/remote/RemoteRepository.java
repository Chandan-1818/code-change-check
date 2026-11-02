package com.analysis.tool.remote;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * PHASE 2: a remote Git repository cloned into a dedicated, isolated temporary
 * workspace.
 *
 * <p>The clone is the only place this tool ever writes for a remote repository:
 * the remote itself is never modified or pushed to, and the user's own local
 * clones are never touched. The workspace is deleted by {@link #close()}.
 *
 * <p>Security (PHASE 13):
 * <ul>
 *   <li>URLs are validated before any network access ({@link #isValidUrl}).</li>
 *   <li>Credentials are only ever taken from an already-configured mechanism
 *       (JGit's {@link CredentialsProvider#getDefault()}); the tool never asks
 *       for or stores a password or token.</li>
 *   <li>Credentials embedded in a URL are redacted before anything is printed
 *       ({@link #redactCredentials}).</li>
 *   <li>The clone has a network timeout so an unreachable host cannot hang the
 *       tool.</li>
 *   <li>The repository name is sanitised before it is used in any path
 *       ({@link #sanitizeProjectName}).</li>
 * </ul>
 */
public class RemoteRepository implements AutoCloseable {

    /** How long a clone may take before it is aborted (seconds). */
    public static final int DEFAULT_CLONE_TIMEOUT_SECONDS = 120;

    private static final String[] URL_SCHEMES = {
            "http://", "https://", "git://", "ssh://", "git+ssh://", "file://", "ftp://", "ftps://"
    };

    /** scp-like syntax, e.g. git@github.com:user/repo.git */
    private static final Pattern SCP_LIKE =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9._-]+:[^\\s]+$");

    private static final Pattern CREDENTIALS =
            Pattern.compile("^(?<scheme>[A-Za-z][A-Za-z0-9+.-]*://)(?<userinfo>[^/@\\s]+)@");

    private final Path directory;
    private final String url;
    private final String projectName;
    private final String defaultBranch;
    private final String latestCommit;
    private final String previousCommit;

    private RemoteRepository(Path directory, String url, String projectName,
                             String defaultBranch, String latestCommit, String previousCommit) {
        this.directory = directory;
        this.url = url;
        this.projectName = projectName;
        this.defaultBranch = defaultBranch;
        this.latestCommit = latestCommit;
        this.previousCommit = previousCommit;
    }

    /**
     * Validates the URL and clones it into a fresh temporary workspace.
     *
     * @param url            the repository URL (remote, {@code file://} or a
     *                       local Git repository path)
     * @param timeoutSeconds network timeout for the clone
     * @throws RemoteRepositoryException when the URL is invalid or the clone
     *                                   fails (unreachable host, missing
     *                                   credentials, not a Git repository)
     */
    public static RemoteRepository open(String url, int timeoutSeconds) throws RemoteRepositoryException {
        if (!isValidUrl(url)) {
            throw new RemoteRepositoryException(
                    "Invalid Git repository URL: " + redactCredentials(url)
                            + " (expected https://, ssh://, git://, file:// or user@host:path)");
        }

        Path workspace;
        try {
            workspace = Files.createTempDirectory("rts-remote-");
        } catch (IOException e) {
            throw new RemoteRepositoryException("Could not create a temporary workspace for the clone", e);
        }

        String projectName = sanitizeProjectName(projectNameFromUrl(url));

        Git git = null;
        try {
            org.eclipse.jgit.api.CloneCommand clone = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(workspace.toFile())
                    .setTimeout(timeoutSeconds);
            // Only ever use an already-configured credential mechanism; the tool
            // never prompts for or stores a password or token (PHASE 2/13).
            CredentialsProvider credentials = CredentialsProvider.getDefault();
            if (credentials != null) {
                clone.setCredentialsProvider(credentials);
            }
            git = clone.call();

            Repository repository = git.getRepository();
            String branch = repository.getBranch();
            ObjectId head = repository.resolve(Constants.HEAD);
            if (head == null) {
                throw new RemoteRepositoryException(
                        "Repository " + redactCredentials(url) + " has no commits to compare.");
            }

            String latest;
            String previous;
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit tip = walk.parseCommit(head);
                latest = abbreviate(tip);
                if (tip.getParentCount() > 0) {
                    previous = abbreviate(walk.parseCommit(tip.getParent(0)));
                } else {
                    previous = null;
                }
            }

            return new RemoteRepository(workspace, url, projectName, branch, latest, previous);
        } catch (GitAPIException e) {
            deleteRecursively(workspace);
            throw new RemoteRepositoryException(
                    "Could not clone " + redactCredentials(url) + ": " + e.getMessage(), e);
        } catch (IOException e) {
            deleteRecursively(workspace);
            throw new RemoteRepositoryException(
                    "Could not read the cloned repository " + redactCredentials(url) + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            deleteRecursively(workspace);
            throw new RemoteRepositoryException(
                    "Could not clone " + redactCredentials(url) + ": " + e.getMessage(), e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    /**
     * True when the argument is a usable Git repository location.
     *
     * <p>Accepts the standard URL schemes, scp-like {@code user@host:path}
     * syntax, and an existing local directory that is a Git repository (so a
     * developer can point the tool at a path as well as a URL). Anything with
     * whitespace or without a recognisable form is rejected before any network
     * access is attempted.
     */
    public static boolean isValidUrl(String url) {
        if (url == null) {
            return false;
        }
        String candidate = url.trim();
        if (candidate.isEmpty() || !candidate.equals(url) || containsWhitespace(candidate)) {
            return false;
        }

        String lower = candidate.toLowerCase(Locale.ROOT);
        for (String scheme : URL_SCHEMES) {
            if (lower.startsWith(scheme)) {
                // Must have something after the scheme.
                return candidate.length() > scheme.length()
                        && hasRepositorySegment(candidate.substring(scheme.length()));
            }
        }
        if (SCP_LIKE.matcher(candidate).matches()) {
            return true;
        }

        // A local path is accepted only when it really is a Git repository, so
        // a typo cannot be mistaken for a remote URL.
        try {
            Path path = new File(candidate).toPath();
            return Files.isDirectory(path.resolve(".git")) || Files.isRegularFile(path.resolve(".git"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean hasRepositorySegment(String rest) {
        return !rest.replaceAll("^/+", "").isEmpty();
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Strips any {@code user:password@} fragment so it can never be logged. */
    public static String redactCredentials(String url) {
        if (url == null) {
            return "";
        }
        return CREDENTIALS.matcher(url).replaceFirst("${scheme}***@");
    }

    /**
     * Derives a display/name token from the URL, e.g.
     * {@code https://github.com/user/sample-java-project.git} ->
     * {@code sample-java-project}.
     */
    public static String projectNameFromUrl(String url) {
        if (url == null) {
            return "repository";
        }
        String value = url.trim();
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        int slash = value.lastIndexOf('/');
        String name = slash < 0 ? value : value.substring(slash + 1);
        int colon = name.lastIndexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - ".git".length());
        }
        return name.isEmpty() ? "repository" : name;
    }

    /**
     * Reduces an arbitrary repository name to a safe filesystem/path token, so
     * a hostile repository name cannot escape the output directory. Never
     * returns an empty string and never contains a path separator or "..".
     */
    public static String sanitizeProjectName(String name) {
        if (name == null) {
            return "repository";
        }
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.') {
                safe.append(c);
            }
        }
        String result = safe.toString();
        while (result.startsWith(".")) {
            result = result.substring(1);
        }
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? "repository" : result;
    }

    private static String abbreviate(RevCommit commit) {
        String name = commit.getName();
        return name.length() > 7 ? name.substring(0, 7) : name;
    }

    /** The temporary workspace that holds the clone; deleted on {@link #close()}. */
    public Path getDirectory() {
        return directory;
    }

    /** The workspace path as a string, for the analysis pipeline. */
    public String getPath() {
        return directory.toString();
    }

    public String getUrl() {
        return url;
    }

    /** The workspace-safe project name (never contains a path separator). */
    public String getProjectName() {
        return projectName;
    }

    /** The branch that was checked out, i.e. the repository's default branch. */
    public String getDefaultBranch() {
        return defaultBranch;
    }

    /** Abbreviated id of the latest commit on the default branch. */
    public String getLatestCommit() {
        return latestCommit;
    }

    /**
     * Abbreviated id of the commit before the latest one, or null when the
     * repository has only one commit (two revisions are then required and the
     * caller must explain that).
     */
    public String getPreviousCommit() {
        return previousCommit;
    }

    public boolean hasPreviousCommit() {
        return previousCommit != null;
    }

    /**
     * Throws when a caller asks for the previous commit of a single-commit
     * repository, with an explanation instead of a confusing failure.
     */
    public String requirePreviousCommit() throws RemoteRepositoryException {
        if (previousCommit == null) {
            throw new RemoteRepositoryException(
                    "Repository " + projectName + " contains only one commit; at least two revisions "
                            + "are required to compare changes.");
        }
        return previousCommit;
    }

    @Override
    public void close() {
        deleteRecursively(directory);
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                path.toFile().setWritable(true);
                path.toFile().delete();
            });
        } catch (IOException ignored) {
            // Best-effort cleanup of a temporary directory.
        }
    }
}
