package com.thesis.qualitygateanalyzer.domain.qualitygate;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * A workflow run from GitHub Actions.
 */
@Value
@Builder
public class WorkflowRun {
    long id;
    String workflowFile;
    String workflowName;
    String headSha;
    String conclusion;    // success, failure, canceled
    String status;        // completed, in_progress, queued
    String event;         // push, pull_request
    Integer prNumber;     // null if not PR-related
    Instant createdAt;
    String htmlUrl;

    public boolean isFailed() {
        return "failure".equalsIgnoreCase(conclusion);
    }

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(conclusion);
    }

    public boolean isForPR() {
        return prNumber != null || "pull_request".equalsIgnoreCase(event);
    }
}


