package com.analysis.tool.git;

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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
