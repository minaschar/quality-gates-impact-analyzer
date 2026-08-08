package com.thesis.qualitygateanalyzer.dto.response;

import com.thesis.qualitygateanalyzer.domain.enums.ImpactTrend;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateCategory;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Before/after quality impact of a single quality gate tool's introduction.
 */
@Getter
@Builder
@AllArgsConstructor
public class ToolComparisonDto {

    private QualityGateTool tool;
    private QualityGateCategory toolCategory;
    private Instant introducedAt;
    private String introducedCommitSha;

    private int samplesBefore;
    private int samplesAfter;

    private Double improvementScore;
    private ImpactTrend trend;

    /**
     * Per-metric before/after breakdown, keyed by metric name (bugs, coverage, ...).
     */
    private Map<String, MetricComparisonDto> metrics;
}
