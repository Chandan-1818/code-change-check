package com.analysis.tool.git;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * GitAnalyzer opens a Git repository at a given project path
 * and provides access to its history for change detection.
 *
 * Supported operations (current scope):
 * - Reading the current branch name
 * - Listing file paths changed between two revisions (file-level diff)
 *
 * Not yet supported (future phases):
 * - Class/method-level change detection (requires JavaParser integration)
 */
public class GitAnalyzer {

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
     * (e.g. "HEAD~1" and "HEAD", or two commit hashes).
     *
     * This is file-level detection only. It does not yet distinguish
     * between added/modified/deleted at the class or method level.
     */
    public List<String> getChangedFiles(String oldRev, String newRev) throws IOException {
        List<String> changedFiles = new ArrayList<>();

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
                    // Use the "new path" for additions/modifications,
                    // and "old path" for deletions (new path is "/dev/null" then)
                    String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE
                            ? entry.getOldPath()
                            : entry.getNewPath();
                    changedFiles.add(path);
                }
            }
        }

        return changedFiles;
    }

    public void close() {
        repository.close();
    }
}
