package com.analysis.tool.testsupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
