package com.analysis.tool.execution;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The classpath handed to the oracle capture comes from the target revision's
 * own Maven configuration. A revision that is not a Maven project keeps the
 * JDK-only behaviour; a revision whose dependencies cannot be resolved fails
 * with Maven's own diagnostic, never with a misleading missing-package error.
 *
 * The unresolved-dependency test starts a real Maven process (a few seconds).
 */
class MavenClasspathResolverTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    private static final String BROKEN_POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>demo</groupId>
              <artifactId>broken-dependencies</artifactId>
              <version>1.0</version>
              <dependencies>
                <dependency>
                  <groupId>com.example.this.artifact.does.not.exist</groupId>
                  <artifactId>nowhere</artifactId>
                  <version>1.0</version>
                </dependency>
              </dependencies>
            </project>
            """;

    @TempDir
    Path repoDir;

    private String commit(Git git, String relativePath, String content, String message) throws Exception {
        Path file = repoDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        git.add().addFilepattern(".").call();
        return git.commit().setMessage(message).setAuthor(IDENT).setCommitter(IDENT)
                .setSign(false).call().getName();
    }

    @Test
    void nonMavenRevisionKeepsTheJdkOnlyClasspath() throws Exception {
        String revision;
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            revision = commit(git, "src/main/java/demo/Calculator.java",
                    "package demo;\npublic class Calculator { public int add(int a, int b) { return a + b; } }\n",
                    "plain java");
        }

        assertEquals("", new MavenClasspathResolver().resolve(repoDir.toString(), revision));
    }

    @Test
    void unresolvableDependenciesProduceAPreciseDiagnostic() throws Exception {
        String revision;
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            revision = commit(git, "pom.xml", BROKEN_POM, "broken dependencies");
        }

        IOException error = assertThrows(IOException.class,
                () -> new MavenClasspathResolver().resolve(repoDir.toString(), revision));

        assertTrue(error.getMessage().contains("Maven could not resolve the dependency classpath"),
                error.getMessage());
        assertTrue(error.getMessage().contains(revision), error.getMessage());
        assertTrue(error.getMessage().contains("Maven output"), error.getMessage());
    }
}
