package com.thesis.qualitygateanalyzer.dto.response;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * One point on the quality-metrics timeline chart: a classified snapshot for a given tool.
 */
@Getter
@Builder
@AllArgsConstructor
public class TimelinePointDto {

    private QualityGateTool tool;
    private String classification; // BEFORE / AFTER
    private Integer pullRequestNumber;
    private String commitSha;
    private Instant commitDate;

    private Integer bugs;
    private Integer vulnerabilities;
    private Integer codeSmells;
    private Integer securityHotspots;
    private Double coverage;
    private Double duplicatedLinesDensity;
    private Integer ncloc;
    private Integer complexity;
    private Integer cognitiveComplexity;
    private Integer softwareQualityReliabilityIssues;
    private Integer softwareQualityMaintainabilityIssues;
    private Integer softwareQualitySecurityIssues;
}
