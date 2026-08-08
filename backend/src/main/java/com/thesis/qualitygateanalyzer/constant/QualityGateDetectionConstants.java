package com.thesis.qualitygateanalyzer.constant;

/**
 * Numeric limits used by the detection pipeline. These are compile-time
 * fallback values, distinct from the same-named runtime settings in
 * {@code t_configuration} (which are not currently wired into the detection
 * pipeline — see {@code ConfigurationService}).
 */
public final class QualityGateDetectionConstants {

    private QualityGateDetectionConstants() {
    }

    public static final int WORKFLOW_RUNS_LIMIT = 100;
    public static final int PR_ANALYSIS_LIMIT = 100;
    public static final int SAMPLE_PRS_LIMIT = 5;
    public static final int MAX_BINARY_SEARCH_ITERATIONS = 50;
    public static final int LINEAR_SEARCH_THRESHOLD = 10;
}
