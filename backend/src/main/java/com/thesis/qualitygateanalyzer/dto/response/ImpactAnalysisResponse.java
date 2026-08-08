package com.thesis.qualitygateanalyzer.dto.response;

import com.thesis.qualitygateanalyzer.domain.enums.ImpactTrend;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * E2E quality impact analysis result for a repository: whether code quality improved after
 * its quality gate tool(s) were introduced, plus data structured for UI visualization
 * (before/after comparison cards and a timeline chart).
 */
@Getter
@Builder
@AllArgsConstructor
public class ImpactAnalysisResponse {

    private String owner;
    private String repo;
    private String repositoryUrl;

    /**
     * False when no quality gate was detected for this repository -- in that case
     * {@link #comparisons} and {@link #timeline} are empty and nothing was persisted.
     */
    private boolean hasQualityGate;

    private int toolsAnalyzed;
    private Double overallImprovementScore;
    private ImpactTrend overallTrend;

    private Instant dateRangeStart;
    private Instant dateRangeEnd;
    private Instant computedAt;

    /**
     * True if this result was served from a previously saved analysis rather than
     * recomputed. Only meaningful on the POST response.
     */
    private boolean cached;

    private List<ToolComparisonDto> comparisons;
    private List<TimelinePointDto> timeline;
}
