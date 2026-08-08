package com.thesis.qualitygateanalyzer.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Direction of change in quality metrics before vs. after a quality gate tool's introduction.
 */
@Getter
@RequiredArgsConstructor
public enum ImpactTrend {

    IMPROVED("Improved", "Quality metrics got better after introduction"),
    DEGRADED("Degraded", "Quality metrics got worse after introduction"),
    UNCHANGED("Unchanged", "No meaningful change in quality metrics"),
    INSUFFICIENT_DATA("Insufficient Data", "Not enough before/after samples to compare");

    private final String displayName;
    private final String description;

    /**
     * Classify a percentage delta (positive = improvement, e.g. issue counts going down or
     * coverage going up, already sign-normalized by the caller) into a trend, with a small
     * dead zone around zero treated as no meaningful change.
     */
    public static ImpactTrend fromDeltaPercent(Double deltaPercent) {
        if (deltaPercent == null) return INSUFFICIENT_DATA;
        if (deltaPercent > 5.0) return IMPROVED;
        if (deltaPercent < -5.0) return DEGRADED;
        return UNCHANGED;
    }
}
