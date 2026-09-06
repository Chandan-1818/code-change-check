package com.analysis.tool.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;

/**
 * GitAnalyzer opens a Git repository at a given project path
 * and provides access to its history for change detection.
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

    public void close() {
        repository.close();
    }
}
