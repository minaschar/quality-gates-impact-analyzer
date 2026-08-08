package com.thesis.qualitygateanalyzer.domain.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateCategory;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Quality gate detected from static configuration detection.
 */
@Value
@Builder
public class QualityGateDetection {
    QualityGateTool tool;
    QualityGateCategory category;
    String sourceFile;
    SourceType sourceType;
    List<String> evidenceFound;
    double confidenceScore;
    Boolean triggersOnPR;  // null if unknown
    String associatedWorkflow;  // For build tool configs linked to a workflow

    public enum SourceType {
        WORKFLOW_ACTION,      // GitHub Action in workflow
        WORKFLOW_COMMAND,     // Command in workflow (mvn, Gradle, npm)
        BUILD_TOOL,           // pom.xml, build.gradle, package.json
        CONFIG_FILE           // Dedicated config file
    }

    public boolean isRelevantForThesis() {
        return tool.isRelevantForThesis();
    }
}
