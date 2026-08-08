package com.thesis.qualitygateanalyzer.domain.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Branch protection settings for a branch.
 */
@Value
@Builder
public class BranchProtection {
    String branch;
    boolean isProtected;
    boolean requiresStatusChecks;
    boolean strictStatusChecks;          // Requires branch to be up-to-date
    List<RequiredCheck> requiredChecks;  // List of required status checks
    boolean enforceAdmins;               // Even admins must follow rules
    boolean allowForcePushes;
    boolean allowDeletions;

    /**
     * A required status check.
     */
    @Value
    @Builder
    public static class RequiredCheck {
        String context;     // Check name, e.g., "SonarCloud Code Analysis"
        Integer appId;      // GitHub App ID if applicable
        String appSlug;     // Matched app slug (e.g., "sonar cloud")
        QualityGateTool matchedTool;  // Matched QG tool if any
    }

    /**
     * Check if a specific tool is a required check.
     */
    public boolean isToolRequired(QualityGateTool tool) {
        if (!requiresStatusChecks || requiredChecks == null) {
            return false;
        }
        return requiredChecks.stream()
                .anyMatch(check -> check.getMatchedTool() == tool);
    }

    /**
     * Check if a check context/name is required.
     */
    public boolean isCheckRequired(String checkName) {
        if (!requiresStatusChecks || requiredChecks == null || checkName == null) {
            return false;
        }
        String lowerName = checkName.toLowerCase();
        return requiredChecks.stream()
                .anyMatch(check -> check.getContext() != null &&
                        check.getContext().toLowerCase().contains(lowerName));
    }

    /**
     * Get all required QG tools.
     */
    public Set<QualityGateTool> getRequiredQGTools() {
        if (!requiresStatusChecks || requiredChecks == null) {
            return Set.of();
        }
        return requiredChecks.stream()
                .map(RequiredCheck::getMatchedTool)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Check if any QG is required.
     */
    public boolean hasRequiredQualityGate() {
        return !getRequiredQGTools().isEmpty();
    }
}
