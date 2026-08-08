package com.thesis.qualitygateanalyzer.constant;

/**
 * Keys for runtime configuration values stored in {@code t_configuration}.
 */
public final class ConfigurationConstants {

    private ConfigurationConstants() {
    }

    public static final String GITHUB_TOKEN = "GITHUB_TOKEN";
    public static final String SAMPLE_PRS_LIMIT = "SAMPLE_PRS_LIMIT";
    public static final String WORKFLOW_RUNS_LIMIT = "WORKFLOW_RUNS_LIMIT";
    public static final String PR_ANALYSIS_LIMIT = "PR_ANALYSIS_LIMIT";
    public static final String CHECK_RUNS_PER_COMMIT = "CHECK_RUNS_PER_COMMIT";
    public static final String MAX_BINARY_SEARCH_ITERATIONS = "MAX_BINARY_SEARCH_ITERATIONS";
    public static final String LINEAR_SEARCH_THRESHOLD = "LINEAR_SEARCH_THRESHOLD";
    public static final String SAMPLE_PRS_TO_RETURN = "SAMPLE_PRS_TO_RETURN";
    public static final String ENABLE_PR_FALLBACK = "ENABLE_PR_FALLBACK";
    public static final String ENABLE_HISTORY_ANALYSIS = "ENABLE_HISTORY_ANALYSIS";
    public static final String ENABLE_EXTERNAL_CHECK_DETECTION = "ENABLE_EXTERNAL_CHECK_DETECTION";
}
