package com.thesis.qualitygateanalyzer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class QualityMetricSnapshotDto {

    private Integer pullRequestNumber;
    private String pullRequestTitle;
    private String pullRequestUrl;
    private String pullRequestState;
    private Boolean pullRequestMerged;
    private String baseBranchName;
    private String headBranchName;

    private String analysisRole;
    private String commitHash;
    private String firstPullRequestCommitHash;
    private String closingCommitHash;

    private String sonarProjectKey;
    private String sonarProjectName;
    private String sonarDashboardUrl;
    private String sonarTaskId;
    private String sonarAnalysisId;
    private String qualityGateStatus;

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

    private Object issuesSummary;
    private String categories;

    private Long sourceRowId;
    private Instant sourceCreatedAt;
    private Instant sourceUpdatedAt;
}
