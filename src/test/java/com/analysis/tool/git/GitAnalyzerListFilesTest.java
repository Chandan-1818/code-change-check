package com.analysis.tool.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 12 prerequisite: GitAnalyzer must be able to list every file
 * present at a revision (used for auto-discovering source/test pairs).
 * Uses a throwaway repo in a temp directory.
 */
public class GitAnalyzerListFilesTest {

    private static final String SRC = "src/main/java/demo/Calculator.java";
    private static final String TEST = "src/test/java/demo/CalculatorTest.java";
    private static final String POM = "pom.xml";
    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    @TempDir
    Path repoDir;

    @Test
    public void shouldListAllFilesPresentAtRevision() throws Exception {
        String commitId;
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(SRC, "package demo;\npublic class Calculator {}\n");
            write(TEST, "package demo;\npublic class CalculatorTest {}\n");
            write(POM, "<project/>\n");
            git.add().addFilepattern(".").call();
            commitId = git.commit()
                    .setMessage("V1")
                    .setAuthor(IDENT)
                    .setCommitter(IDENT)
                    .setSign(false)
                    .call()
                    .getName();
        }

        GitAnalyzer analyzer = new GitAnalyzer(repoDir.toString());
        List<String> files;
        try {
            files = analyzer.listFilesAtRevision(commitId);
        } finally {
            analyzer.close();
        }

        assertEquals(3, files.size(), "Files were: " + files);
        assertTrue(files.contains(SRC));
        assertTrue(files.contains(TEST));
        assertTrue(files.contains(POM));
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = repoDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
