package com.analysis.tool.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SamplePathsTest {

    @Test
    public void propertyWinsOverEnvironmentAndDefault() {
        assertEquals("P", SamplePaths.resolve("P", "E"));
    }

    @Test
    public void environmentWinsOverDefault() {
        assertEquals("E", SamplePaths.resolve(null, "E"));
        assertEquals("E", SamplePaths.resolve("  ", "E"));
    }

    @Test
    public void defaultIsUsedWhenNothingIsSet() {
        assertEquals(SamplePaths.DEFAULT_PATH, SamplePaths.resolve(null, null));
        assertEquals(SamplePaths.DEFAULT_PATH, SamplePaths.resolve("", ""));
    }

    @Test
    public void aDifferentPathSuppliedBySystemPropertyIsAdopted() {
        assertEquals("E:/elsewhere/sample-project",
                SamplePaths.resolve("E:/elsewhere/sample-project", null));
        assertEquals("E:/elsewhere/sample-project",
                SamplePaths.resolve("E:/elsewhere/sample-project", "F:/ignored"));
    }

    @Test
    public void missingProjectProducesAClearDiagnostic() {
        Path missing = Paths.get(SamplePaths.DEFAULT_PATH, "definitely-not-here");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> SamplePaths.validate(missing));

        String message = thrown.getMessage();
        assertTrue(message.contains(SamplePaths.PROPERTY),
                "diagnostic must name the system property; was: " + message);
        assertTrue(message.contains(SamplePaths.ENVIRONMENT_VARIABLE),
                "diagnostic must name the environment variable; was: " + message);
        assertTrue(message.contains(missing.toString()),
                "diagnostic must show the path that was tried; was: " + message);
    }

    @Test
    public void nullPathProducesAClearDiagnostic() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> SamplePaths.validate(null));

        assertTrue(thrown.getMessage().contains(SamplePaths.PROPERTY));
    }

    @Test
    public void anExistingDirectoryIsAccepted(@TempDir Path directory) {
        assertEquals(directory, SamplePaths.validate(directory));
    }
}
