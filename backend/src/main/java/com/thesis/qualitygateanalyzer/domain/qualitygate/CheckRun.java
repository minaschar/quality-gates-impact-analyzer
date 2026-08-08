package com.thesis.qualitygateanalyzer.domain.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * A check run from GitHub (can be from Actions or external apps).
 */
@Value
@Builder
public class CheckRun {
    long id;
    String name;
    String appSlug;        // e.g., "sonar cloud", "codecov"
    String conclusion;     // success, failure, neutral, etc.
    String status;
    String outputTitle;
    String outputSummary;
    String headSha;
    Instant completedAt;

    // Matched tool info
    QualityGateTool matchedTool;
    double matchConfidence;

    public boolean isFailed() {
        return "failure".equalsIgnoreCase(conclusion) ||
                "action_required".equalsIgnoreCase(conclusion);
    }

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(conclusion);
    }

    public boolean isQualityGateRelated() {
        return matchedTool != null;
    }

    public boolean isThesisRelevant() {
        return matchedTool != null && matchedTool.isRelevantForThesis();
    }

    public String getFailureMessage() {
        if (outputTitle != null && !outputTitle.isBlank()) {
            return outputTitle;
        }
        return outputSummary;
    }
}
