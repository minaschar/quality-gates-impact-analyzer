package com.thesis.qualitygateanalyzer.dto.response;

import com.thesis.qualitygateanalyzer.domain.enums.ImpactTrend;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Before/after statistics for a single quality metric (bugs, coverage, ...).
 */
@Getter
@Builder
@AllArgsConstructor
public class MetricComparisonDto {

    private Double avgBefore;
    private Double avgAfter;
    private Double medianBefore;
    private Double medianAfter;
    private Double deltaAbsolute;
    private Double deltaPercent;
    private ImpactTrend trend;
}
