package com.thesis.qualitygateanalyzer.dto.response;

import com.thesis.qualitygateanalyzer.domain.enums.ImpactTrend;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Summary row for the impact-analysis list endpoint.
 */
@Getter
@Builder
@AllArgsConstructor
public class ImpactAnalysisSummaryDto {

    private String owner;
    private String repo;
    private String repositoryUrl;
    private int toolsAnalyzed;
    private Double overallImprovementScore;
    private ImpactTrend overallTrend;
    private Instant computedAt;
}
