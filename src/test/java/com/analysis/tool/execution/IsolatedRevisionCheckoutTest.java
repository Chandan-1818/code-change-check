package com.analysis.tool.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.analysis.tool.testsupport.SamplePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for H8's correct-revision execution (GAP-14).
 *
 * It clones the sample project through {@link IsolatedRevisionCheckout} at a
 * pinned revision and proves two things: the checked-out sources really are
 * the requested revision, and closing the checkout deletes the temporary
 * directory so nothing is left behind. No Maven build is launched, so this is
 * much faster than the execution tests.
 */
public class IsolatedRevisionCheckoutTest {

    private static final String VERSION_5_COMMIT = "268a165";

    @TempDir
    Path tempDir;

    @Test
    public void checksOutTheRequestedRevisionAndDeletesItOnClose() throws Exception {
        Path checkoutDir;
        try (IsolatedRevisionCheckout checkout =
                     IsolatedRevisionCheckout.open(SamplePaths.SAMPLE_PROJECT_PATH, VERSION_5_COMMIT)) {
            checkoutDir = checkout.getDirectory();
            assertTrue(Files.isDirectory(checkoutDir), "The checkout directory must exist while open");

            Path multiplierTest = checkoutDir.resolve("src/test/java/com/sample/MultiplierTest.java");
            assertTrue(Files.exists(multiplierTest),
                    "The V5 test tree must be present in the isolated checkout");
            String content = Files.readString(multiplierTest, StandardCharsets.UTF_8);
            assertTrue(content.contains("testMultiply"),
                    "The checked-out sources must belong to the requested revision");
        }

        assertFalse(Files.exists(checkoutDir),
                "Closing the checkout must delete the temporary clone");
    }

    @Test
    public void unknownRevisionFailsCleanly() {
        assertThrows(IOException.class,
                () -> IsolatedRevisionCheckout.open(SamplePaths.SAMPLE_PROJECT_PATH, "0000000deadbeef"),
                "An unknown revision must surface as an IOException, not a leak or a silent success");
    }

    @Test
    public void exposesTheDirectoryAsAPathString() throws Exception {
        try (IsolatedRevisionCheckout checkout =
                     IsolatedRevisionCheckout.open(SamplePaths.SAMPLE_PROJECT_PATH, VERSION_5_COMMIT)) {
            assertTrue(checkout.getPath().equals(checkout.getDirectory().toString()));
            assertTrue(Files.isDirectory(Path.of(checkout.getPath())));
        }
    }
}
