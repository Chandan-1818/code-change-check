package com.analysis.tool.remote;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PHASE 14 scenarios 6-11: remote Git repository support.
 *
 * <p>All repositories are throwaway local Git repositories addressed through a
 * {@code file://} URL, so these tests never touch the public internet and can
 * run offline.
 */
class RemoteRepositoryTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    @TempDir
    Path sourceRepo;

    @Test
    void shouldAcceptSupportedUrlFormsAndRejectMalformedOnes() throws Exception {
        assertTrue(RemoteRepository.isValidUrl("https://github.com/user/project.git"));
        assertTrue(RemoteRepository.isValidUrl("http://example.com/user/project"));
        assertTrue(RemoteRepository.isValidUrl("ssh://git@example.com/user/project.git"));
        assertTrue(RemoteRepository.isValidUrl("git@github.com:user/project.git"));
        assertTrue(RemoteRepository.isValidUrl("file:///D:/tmp/project"));

        assertFalse(RemoteRepository.isValidUrl(null));
        assertFalse(RemoteRepository.isValidUrl(""));
        assertFalse(RemoteRepository.isValidUrl("   "));
        assertFalse(RemoteRepository.isValidUrl("not a repository url"));
        assertFalse(RemoteRepository.isValidUrl("https://"));
        assertFalse(RemoteRepository.isValidUrl("https:// "));
        assertFalse(RemoteRepository.isValidUrl("C:/definitely/not/a/git/repo"));
    }

    @Test
    void shouldAcceptAnExistingLocalGitRepositoryPath() throws Exception {
        initRepository("v1", null);
        assertTrue(RemoteRepository.isValidUrl(sourceRepo.toString()),
                "An existing local Git repository must be accepted as a location");
    }

    @Test
    void shouldRedactEmbeddedCredentials() {
        assertEquals("https://***@github.com/user/project.git",
                RemoteRepository.redactCredentials("https://user:secret@github.com/user/project.git"));
        assertEquals("https://github.com/user/project.git",
                RemoteRepository.redactCredentials("https://github.com/user/project.git"));
        assertEquals("", RemoteRepository.redactCredentials(null));
    }

    @Test
    void shouldDeriveAndSanitiseProjectNames() {
        assertEquals("project",
                RemoteRepository.projectNameFromUrl("https://github.com/user/project.git"));
        assertEquals("project",
                RemoteRepository.projectNameFromUrl("https://github.com/user/project/"));
        assertEquals("project",
                RemoteRepository.projectNameFromUrl("git@github.com:user/project.git"));
        assertEquals("repository", RemoteRepository.projectNameFromUrl(null));

        // A hostile repository name must not be able to escape a directory.
        assertEquals("etcpasswd", RemoteRepository.sanitizeProjectName("../../etc/passwd"));
        assertFalse(RemoteRepository.sanitizeProjectName("../../etc/passwd").contains("/"));
        assertFalse(RemoteRepository.sanitizeProjectName("..\\..\\windows").contains("\\"));
        assertEquals("windows", RemoteRepository.sanitizeProjectName("..\\..\\windows"));
        assertEquals("repository", RemoteRepository.sanitizeProjectName(".."));
        assertEquals("repository", RemoteRepository.sanitizeProjectName(""));
        assertEquals("repository", RemoteRepository.sanitizeProjectName(null));
    }

    @Test
    void shouldCloneARepositoryAndReportItsRevisions() throws Exception {
        initRepository("v1", "v2");
        String url = sourceRepo.toUri().toString();

        try (RemoteRepository repository = RemoteRepository.open(url, 60)) {
            assertEquals(RemoteRepository.sanitizeProjectName(RemoteRepository.projectNameFromUrl(url)),
                    repository.getProjectName());
            assertFalse(repository.getProjectName().isEmpty());
            assertFalse(repository.getProjectName().contains("/"));
            assertNotNull(repository.getDefaultBranch());
            assertNotNull(repository.getLatestCommit());
            assertNotNull(repository.getPreviousCommit());
            assertTrue(repository.hasPreviousCommit());
            assertEquals(repository.getPreviousCommit(), repository.requirePreviousCommit());
            assertTrue(Files.isDirectory(repository.getDirectory().resolve(".git")),
                    "The clone must be a real Git checkout");
            assertTrue(Files.isRegularFile(repository.getDirectory().resolve("README.md")));
        }
    }

    @Test
    void shouldDeleteItsWorkspaceOnClose() throws Exception {
        initRepository("v1", "v2");
        Path workspace;
        try (RemoteRepository repository = RemoteRepository.open(sourceRepo.toUri().toString(), 60)) {
            workspace = repository.getDirectory();
            assertTrue(Files.isDirectory(workspace));
        }
        assertFalse(Files.exists(workspace), "The temporary workspace must be removed on close");
    }

    @Test
    void shouldReportAMissingPreviousCommitForASingleCommitRepository() throws Exception {
        initRepository("only", null);

        try (RemoteRepository repository = RemoteRepository.open(sourceRepo.toUri().toString(), 60)) {
            assertFalse(repository.hasPreviousCommit());
            assertNull(repository.getPreviousCommit());
            RemoteRepositoryException error =
                    assertThrows(RemoteRepositoryException.class, repository::requirePreviousCommit);
            assertTrue(error.getMessage().contains("only one commit"),
                    "The single-commit case must be explained, not silently ignored");
        }
    }

    @Test
    void shouldRejectAnInvalidUrlBeforeAnyNetworkAccess() {
        assertThrows(RemoteRepositoryException.class, () -> RemoteRepository.open("not-a-url", 5));
    }

    @Test
    void shouldReportACloneFailureForAnUnreachableRepository() throws Exception {
        Path missing = sourceRepo.resolve("does-not-exist");
        String url = missing.toUri().toString();

        RemoteRepositoryException error =
                assertThrows(RemoteRepositoryException.class, () -> RemoteRepository.open(url, 10));
        assertTrue(error.getMessage().contains("Could not clone")
                        || error.getMessage().contains("Could not read"),
                "A clone failure must be reported clearly. Actual message: " + error.getMessage());
    }

    /** Initialises {@code sourceRepo} with one or two commits. */
    private void initRepository(String firstMessage, String secondMessage) throws Exception {
        try (Git git = Git.init().setDirectory(sourceRepo.toFile()).call()) {
            write("README.md", "version 1\n");
            git.add().addFilepattern(".").call();
            commit(git, firstMessage);
            if (secondMessage != null) {
                write("README.md", "version 2\n");
                git.add().addFilepattern(".").call();
                commit(git, secondMessage);
            }
        }
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = sourceRepo.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private String commit(Git git, String message) throws Exception {
        return git.commit()
                .setMessage(message)
                .setAuthor(IDENT)
                .setCommitter(IDENT)
                .setSign(false)
                .call()
                .getName();
    }
}
