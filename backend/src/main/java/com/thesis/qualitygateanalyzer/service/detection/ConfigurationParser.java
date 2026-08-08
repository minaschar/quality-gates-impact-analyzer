package com.thesis.qualitygateanalyzer.service.detection;

import com.thesis.qualitygateanalyzer.domain.qualitygate.QualityGateDetection;

import java.util.List;
import java.util.Optional;

/**
 * Parser for static configuration files.
 * Detects quality gates in workflows, build tools, and config files.
 */
public interface ConfigurationParser {

    /**
     * Parse a workflow file for quality gate configurations.
     */
    WorkflowParseResult parseWorkflow(String filePath, String content);

    /**
     * Parse a build tool config file (pom.xml, build.gradle, package.json, etc.)
     */
    List<QualityGateDetection> parseBuildConfig(String filePath, String content, String associatedWorkflow);

    /**
     * Parse a dedicated QG config file.
     */
    Optional<QualityGateDetection> parseConfigFile(String filePath, String content);

    /**
     * Result of parsing a workflow file.
     */
    record WorkflowParseResult(
            List<QualityGateDetection> detections,
            boolean triggersOnPR,
            List<String> buildCommandsFound
    ) {
    }
}
