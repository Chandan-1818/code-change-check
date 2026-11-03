package com.analysis.tool.execution;

import org.eclipse.jgit.api.Git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * H8 (GAP-14): an isolated temporary clone of a repository, checked out at a
 * requested revision.
 *
 * Maven runs inside this clone, so the user's own working tree is never
 * touched and the sources under test always match the revision the selection
 * was computed for. This removes the previous hazard where {@code --execute}
 * ran in whatever happened to be checked out in the caller's tree.
 *
 * {@link #close()} deletes the clone (best effort). Callers that need to keep
 * evidence from a failed run should copy the logs out before closing.
 */
public class IsolatedRevisionCheckout implements AutoCloseable {

    private final Path directory;

    private IsolatedRevisionCheckout(Path directory) {
        this.directory = directory;
    }

    public static IsolatedRevisionCheckout open(String repoPath, String revision) throws IOException {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("rts-checkout-");
        } catch (IOException e) {
            throw new IOException("Could not create a temporary directory for the checkout", e);
        }
        try (Git git = Git.cloneRepository()
                .setURI(new File(repoPath).getAbsoluteFile().toURI().toString())
                .setDirectory(workDir.toFile())
                .call()) {
            git.checkout().setName(revision).setForced(true).call();
            return new IsolatedRevisionCheckout(workDir);
        } catch (Exception e) {
            deleteRecursively(workDir);
            throw new IOException("Could not check out revision " + revision + " from " + repoPath, e);
        }
    }

    public Path getDirectory() {
        return directory;
    }

    public String getPath() {
        return directory.toString();
    }

    @Override
    public void close() {
        deleteRecursively(directory);
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
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
