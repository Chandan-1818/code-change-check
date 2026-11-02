package com.analysis.tool.git;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * GitAnalyzer opens a Git repository at a given project path
 * and provides access to its history for change detection.
 *
 * Supported operations (current scope):
 * - Reading the current branch name
 * - Listing file paths changed between two revisions (file-level diff)
 * - Reading a file's content as it existed at a specific revision
 *
 * Not yet supported (future phases):
 * - Class/method-level change detection (requires JavaParser integration)
 */
public class GitAnalyzer {

    /** How a file changed between two revisions. */
    public enum ChangeType {
        ADDED, MODIFIED, DELETED, RENAMED, COPIED
    }

    /** One entry of the file-level diff: its path, previous path and change type. */
    public static final class ChangedFile {

        private final String path;
        private final String oldPath;
        private final ChangeType changeType;

        public ChangedFile(String path, String oldPath, ChangeType changeType) {
            this.path = path;
            this.oldPath = oldPath;
            this.changeType = changeType;
        }

        /** The current path (the previous path for a deletion). */
        public String getPath() {
            return path;
        }

        /** The previous path (equal to {@link #getPath()} except for renames/copies). */
        public String getOldPath() {
            return oldPath;
        }

        public ChangeType getChangeType() {
            return changeType;
        }

        /** Single-letter marker used for display: A, M, D, R or C. */
        public String getMarker() {
            switch (changeType) {
                case ADDED: return "A";
                case DELETED: return "D";
                case RENAMED: return "R";
                case COPIED: return "C";
                default: return "M";
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChangedFile)) return false;
            ChangedFile that = (ChangedFile) o;
            return Objects.equals(path, that.path)
                    && Objects.equals(oldPath, that.oldPath)
                    && changeType == that.changeType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, oldPath, changeType);
        }

        @Override
        public String toString() {
            return "[" + getMarker() + "] " + path;
        }
    }

    private final Repository repository;

    public GitAnalyzer(String projectPath) throws IOException {
        File gitDir = new File(projectPath, ".git");
        if (!gitDir.exists()) {
            throw new IOException("Not a Git repository (missing .git directory): " + projectPath);
        }
        this.repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .build();
    }

    public String getCurrentBranch() throws IOException {
        return repository.getBranch();
    }

    /**
     * Returns the list of file paths that changed between two revisions
     * (e.g. "HEAD~1" and "HEAD", or two commit hashes), without their
     * change type. See {@link #getChangedFileEntries} for the typed diff.
     */
    public List<String> getChangedFiles(String oldRev, String newRev) throws IOException {
        List<String> changedFiles = new ArrayList<>();
        for (ChangedFile file : getChangedFileEntries(oldRev, newRev)) {
            changedFiles.add(file.getPath());
        }
        return changedFiles;
    }

    /**
     * Returns the file-level diff between two revisions with each entry's
     * change type (added, modified, deleted, renamed or copied).
     *
     * The entry's path is the new path for additions/modifications/renames,
     * and the old path for a deletion (whose new path is "/dev/null").
     *
     * @throws IOException if either revision cannot be resolved
     */
    public List<ChangedFile> getChangedFileEntries(String oldRev, String newRev) throws IOException {
        List<ChangedFile> changedFiles = new ArrayList<>();

        ObjectId oldHead = repository.resolve(oldRev + "^{tree}");
        ObjectId newHead = repository.resolve(newRev + "^{tree}");

        if (oldHead == null) {
            throw new IOException("Could not resolve revision: " + oldRev);
        }
        if (newHead == null) {
            throw new IOException("Could not resolve revision: " + newRev);
        }

        try (ObjectReader reader = repository.newObjectReader()) {
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, oldHead);

            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, newHead);

            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(repository);
                List<DiffEntry> diffs = diffFormatter.scan(oldTreeIter, newTreeIter);

                for (DiffEntry entry : diffs) {
                    ChangeType changeType = toChangeType(entry.getChangeType());
                    // Use the "new path" for additions/modifications/renames,
                    // and "old path" for deletions (new path is "/dev/null" then)
                    String path = changeType == ChangeType.DELETED
                            ? entry.getOldPath()
                            : entry.getNewPath();
                    changedFiles.add(new ChangedFile(path, entry.getOldPath(), changeType));
                }
            }
        }

        return changedFiles;
    }

    private static ChangeType toChangeType(DiffEntry.ChangeType jgitType) {
        switch (jgitType) {
            case ADD:
                return ChangeType.ADDED;
            case DELETE:
                return ChangeType.DELETED;
            case RENAME:
                return ChangeType.RENAMED;
            case COPY:
                return ChangeType.COPIED;
            default:
                return ChangeType.MODIFIED;
        }
    }

    /**
     * True when the given revision can be resolved in this repository
     * (a full or abbreviated commit id, a branch, a tag, or a ref such as
     * {@code HEAD~1}). Used to validate user-supplied revisions before any
     * analysis, so a typo produces a clear message instead of a failure deep
     * inside the pipeline.
     */
    public boolean canResolve(String revision) {
        if (revision == null || revision.trim().isEmpty()) {
            return false;
        }
        try {
            return repository.resolve(revision) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Reads the content of a file as it existed at the given revision.
     *
     * @param revision a commit-ish reference (e.g. "HEAD", "HEAD~1", or a commit hash)
     * @param filePath the path to the file relative to the repository root
     *                 (e.g. "src/main/java/com/sample/Calculator.java")
     * @return the file's content as a UTF-8 string
     * @throws IOException if the revision cannot be resolved or the file
     *                      does not exist at that revision
     */
    public String getFileContentAtRevision(String revision, String filePath) throws IOException {
        ObjectId commitId = repository.resolve(revision);
        if (commitId == null) {
            throw new IOException("Could not resolve revision: " + revision);
        }

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(filePath));

                if (!treeWalk.next()) {
                    throw new IOException("File not found at revision " + revision + ": " + filePath);
                }

                ObjectId blobId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(blobId);
                byte[] bytes = loader.getBytes();

                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * Lists every file path present at the given revision (recursive,
     * forward-slash paths relative to the repository root).
     *
     * @throws IOException if the revision cannot be resolved
     */
    public List<String> listFilesAtRevision(String revision) throws IOException {
        ObjectId commitId = repository.resolve(revision);
        if (commitId == null) {
            throw new IOException("Could not resolve revision: " + revision);
        }

        List<String> files = new ArrayList<>();
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    files.add(treeWalk.getPathString());
                }
            }
        }
        return files;
    }

    public void close() {
        repository.close();
    }
}
