package com.thesis.qualitygateanalyzer.domain.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Workflow that contains quality gate(s).
 */
@Value
@Builder
public class QualityGateWorkflow {
    String workflowFile;       // e.g., ".github/workflows/ci.yml"
    String workflowName;       // e.g., "CI"
    List<QualityGateTool> tools;
    boolean triggersOnPR;
    List<String> buildCommands; // e.g., ["mvn verify", "npm test"]
}
