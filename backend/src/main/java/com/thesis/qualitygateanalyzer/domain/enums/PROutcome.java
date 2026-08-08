package com.thesis.qualitygateanalyzer.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Outcome of a PR that had quality gate failures.
 */
@Getter
@RequiredArgsConstructor
public enum PROutcome {

    /**
     * PR had QG failure, developer fixed it, then merged.
     * EVIDENCE: QG is enforcing (worked as intended)
     */
    FIXED_THEN_MERGED("Fixed Then Merged", true),

    /**
     * PR closed without merge after QG failure.
     * EVIDENCE: QG is enforcing (PR was blocked/abandoned)
     */
    BLOCKED("Blocked", true),

    /**
     * PR merged while QG was still failing.
     * EVIDENCE: QG is NOT enforcing (was bypassed)
     */
    MERGED_WITH_FAILURE("Merged With Failure", false),

    /**
     * PR still open with QG failure.
     * INCONCLUSIVE: Work in progress
     */
    STILL_OPEN("Still Open", null),

    /**
     * PR never had a QG failure.
     * INCONCLUSIVE: Doesn't tell us about enforcement
     */
    NO_FAILURE("No Failure", null);

    private final String displayName;
    private final Boolean enforcementWorked; // true=enforced, false=bypassed, null=inconclusive

    public boolean providesEnforcementEvidence() {
        return enforcementWorked != null;
    }
}
