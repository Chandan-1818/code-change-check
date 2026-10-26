package com.analysis.tool.testsupport;

/**
 * Location of the sample target project used by the integration tests.
 * Resolution order: system property rts.sample, environment variable
 * RTS_SAMPLE_PROJECT, then the original default path.
 */
public final class SamplePaths {

    public static final String DEFAULT_PATH = "D:/PROJECT/SAMPLE-PROJECT/sample-project";
    public static final String PROPERTY = "rts.sample";
    public static final String ENVIRONMENT_VARIABLE = "RTS_SAMPLE_PROJECT";

    public static final String SAMPLE_PROJECT_PATH =
            resolve(System.getProperty(PROPERTY), System.getenv(ENVIRONMENT_VARIABLE));

    private SamplePaths() {
    }

    static String resolve(String propertyValue, String environmentValue) {
        if (propertyValue != null && !propertyValue.trim().isEmpty()) {
            return propertyValue.trim();
        }
        if (environmentValue != null && !environmentValue.trim().isEmpty()) {
            return environmentValue.trim();
        }
        return DEFAULT_PATH;
    }
}
