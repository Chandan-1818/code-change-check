package com.analysis.tool.testsupport;

import org.eclipse.jgit.api.Git;

import java.io.File;
import java.nio.file.Path;

/**
 * Test helper: clones the sample project into an empty directory and checks
 * out a pinned revision, so tests that launch real Maven builds run against a
 * fixed version of the sample instead of whatever is currently checked out
 * in its working tree. Growing the sample project therefore cannot change
 * the numbers these tests assert.
 */
public final class PinnedSampleCheckout {

    private PinnedSampleCheckout() {
    }

    public static String cloneAt(String sourceRepoPath, String revision, Path emptyDirectory) {
        try (Git git = Git.cloneRepository()
                .setURI(new File(sourceRepoPath).getAbsoluteFile().toURI().toString())
                .setDirectory(emptyDirectory.toFile())
                .call()) {
            git.checkout().setName(revision).call();
            return emptyDirectory.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not check out " + revision
                    + " from " + sourceRepoPath, e);
        }
    }
}
