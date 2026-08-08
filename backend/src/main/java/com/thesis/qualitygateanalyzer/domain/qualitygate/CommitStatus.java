package com.thesis.qualitygateanalyzer.domain.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * A commit status posted by an external GitHub App (e.g. SonarCloud, Codecov).
 * Unlike check runs, which originate from GitHub Actions, commit statuses are
 * submitted directly by external applications via the GitHub Statuses API.
 */
@Value
@Builder
public class CommitStatus {
    Long id;
    String state;        // error, failure, pending, success
    String context;      // e.g., "SonarCloud Code Analysis", "codecov/project"
    String description;  // Human-readable description
    String targetUrl;    // Link to the external tool's results
    Instant createdAt;
    Instant updatedAt;

    QualityGateTool matchedTool;
    double matchConfidence;

    public boolean isFailed() {
        return "failure".equalsIgnoreCase(state) || "error".equalsIgnoreCase(state);
    }

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(state);
    }

    public boolean isPending() {
        return "pending".equalsIgnoreCase(state);
    }

    public boolean isQualityGateRelated() {
        return matchedTool != null;
    }

    public boolean isThesisRelevant() {
        return matchedTool != null && matchedTool.isRelevantForThesis();
    }
}
