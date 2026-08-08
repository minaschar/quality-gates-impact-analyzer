package com.thesis.qualitygateanalyzer.domain.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.PROutcome;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * Detection result of a single PR regarding quality gate enforcement.
 */
@Value
@Builder
public class PRDetectionResult {
    int prNumber;
    String prTitle;
    String prUrl;
    String state;          // open, closed
    boolean merged;
    Instant createdAt;
    Instant closedAt;
    Instant mergedAt;

    // Workflow run detection
    List<WorkflowRun> workflowRuns;
    boolean hadWorkflowFailure;
    boolean lastWorkflowRunPassed;

    // Check run detection (verified QG failures)
    List<CheckRun> qualityGateCheckRuns;
    boolean hadVerifiedQGFailure;
    boolean lastQGCheckPassed;
    List<QualityGateTool> failedQGTools;
    List<String> failureMessages;

    // Branch protection context
    boolean qgWasRequiredCheck;  // Was the failed QG a required check?

    // Outcome
    PROutcome outcome;
    String outcomeReason;

    public boolean providesEnforcementEvidence() {
        return hadVerifiedQGFailure && outcome.providesEnforcementEvidence();
    }
}
