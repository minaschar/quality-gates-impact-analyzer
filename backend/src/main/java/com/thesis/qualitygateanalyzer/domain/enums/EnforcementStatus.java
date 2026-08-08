package com.thesis.qualitygateanalyzer.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Final enforcement status determination.
 */
@Getter
@RequiredArgsConstructor
public enum EnforcementStatus {

    // Verified statuses (we have evidence)
    STRICTLY_ENFORCED("Strictly Enforced", "≥95% enforcement rate", true, true),
    MOSTLY_ENFORCED("Mostly Enforced", "≥80% enforcement rate", true, true),
    PARTIALLY_ENFORCED("Partially Enforced", "≥50% enforcement rate", true, false),
    NOT_ENFORCED("Not Enforced", "<50% enforcement rate", true, false),

    // Fallback statuses (no failure evidence)
    QG_ACTIVE_NO_FAILURES("Active - No Failures", "QG runs on PRs but no failures found", false, null),
    QG_NOT_RUNNING_ON_PRS("Not Running on PRs", "QG exists but doesn't run on PRs", false, false),
    QG_NOT_REQUIRED("Not Required", "QG exists but is not a required status check", false, false),
    CONFIGURED_NOT_VERIFIED("Configured - Unverified", "Config found but couldn't verify", false, null),

    // Negative statuses
    NO_QUALITY_GATE("No Quality Gate", "No QG found in repository", false, false),
    INSUFFICIENT_DATA("Insufficient Data", "Not enough data to analyze", false, null);

    private final String displayName;
    private final String description;
    private final boolean verified;
    private final Boolean suitableForStudy; // true, false, or null (maybe)

    /**
     * Determine status from enforcement score.
     */
    public static EnforcementStatus fromScore(double score) {
        if (score >= 0.95) return STRICTLY_ENFORCED;
        if (score >= 0.80) return MOSTLY_ENFORCED;
        if (score >= 0.50) return PARTIALLY_ENFORCED;
        return NOT_ENFORCED;
    }

    public boolean isSuitableForStudy() {
        return Boolean.TRUE.equals(suitableForStudy);
    }
}
