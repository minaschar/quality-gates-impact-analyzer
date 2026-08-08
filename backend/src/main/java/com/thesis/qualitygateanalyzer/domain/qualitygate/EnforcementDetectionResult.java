package com.thesis.qualitygateanalyzer.domain.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.EnforcementStatus;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete enforcement detection result.
 */
@Value
@Builder
public class EnforcementDetectionResult {
    EnforcementStatus status;
    Double score;              // null if not calculable
    String confidence;         // HIGH, MEDIUM, LOW

    // Stats
    int totalPRsChecked;
    int prsWithQGFailures;
    int fixedThenMerged;
    int blocked;
    int mergedWithFailure;
    int stillOpen;

    // Breakdown by tool
    Map<QualityGateTool, ToolStats> byTool;

    // Sample PRs for verification
    List<PRDetectionResult> samplePRs;

    // Branch protection info
    BranchProtectionInfo branchProtection;

    // Fallback info (if no failures found)
    FallbackInfo fallbackInfo;

    String interpretation;

    @Value
    @Builder
    public static class ToolStats {
        QualityGateTool tool;
        int failures;
        int enforced;
        int bypassed;
        boolean isRequiredCheck;  // Is this tool a required status check?

        public Double getEnforcementRate() {
            int total = enforced + bypassed;
            return total > 0 ? (double) enforced / total : null;
        }
    }

    @Value
    @Builder
    public static class BranchProtectionInfo {
        String defaultBranch;
        boolean isProtected;
        boolean hasRequiredStatusChecks;
        List<String> requiredCheckNames;
        Set<QualityGateTool> requiredQGTools;
        Set<QualityGateTool> detectedButNotRequired;  // QGs found but not enforced via branch protection
        boolean enforceAdmins;
    }

    @Value
    @Builder
    public static class FallbackInfo {
        boolean workflowRunsOnPRs;
        int successfulQGChecksFound;
        List<String> configuredEnforcement;
        String likelyReason;
    }
}
