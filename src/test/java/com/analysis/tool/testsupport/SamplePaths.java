package com.analysis.tool.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Location of the sample target project used by the integration tests.
 *
 * The directory is resolved once, in this order:
 * <ol>
 *   <li>the {@code rts.sample} system property (preferred),</li>
 *   <li>the {@code RTS_SAMPLE_PROJECT} environment variable,</li>
 *   <li>the original default path (preserves the historical behaviour).</li>
 * </ol>
 *
 * This is test support only. It is deliberately separate from the target
 * repository path a user passes to the CLIs at run time, so the two cannot be
 * confused.
 */
public final class SamplePaths {

    public static final String DEFAULT_PATH = "D:/PROJECT/SAMPLE-PROJECT/sample-project";
    public static final String PROPERTY = "rts.sample";
    public static final String ENVIRONMENT_VARIABLE = "RTS_SAMPLE_PROJECT";

    public static final String SAMPLE_PROJECT_PATH =
            resolve(System.getProperty(PROPERTY), System.getenv(ENVIRONMENT_VARIABLE));

    private SamplePaths() {
    }

    /**
     * Pure precedence rule: system property, then environment variable, then
     * the built-in default. Blank values are ignored so an empty override falls
     * through to the next source.
     */
    static String resolve(String propertyValue, String environmentValue) {
        if (isSet(propertyValue)) {
            return propertyValue.trim();
        }
        if (isSet(environmentValue)) {
            return environmentValue.trim();
        }
        return DEFAULT_PATH;
    }

    /**
     * Resolves the sample project as a {@link Path} and fails with a clear,
     * actionable diagnostic when it is missing or is not a directory, instead
     * of letting a later git/parse step fail obscurely.
     */
    public static Path sampleProject() {
        return validate(Paths.get(SAMPLE_PROJECT_PATH));
    }

    static Path validate(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            throw new IllegalStateException(
                    "Sample project directory not found: '" + path + "'. "
                            + "Point the tests at your checkout with the system property -D"
                            + PROPERTY + "=<path> (preferred) or the "
                            + ENVIRONMENT_VARIABLE + " environment variable. "
                            + "The default is '" + DEFAULT_PATH + "'.");
        }
        return path;
    }

    private static boolean isSet(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
