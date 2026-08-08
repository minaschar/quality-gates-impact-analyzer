package com.thesis.qualitygateanalyzer.entity.impactanalysis;

import com.thesis.qualitygateanalyzer.domain.enums.ImpactTrend;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateCategory;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.entity.BaseEntity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;

/**
 * Aggregated before/after quality comparison for a single quality gate tool: how many
 * samples fell on each side of its introduction date, and the per-metric statistics
 * (avg/median/delta/trend) computed from them.
 */
@Entity
@Table(name = "t_impact_comparison", indexes = {
        @Index(name = "idx_impact_comparison_analysis", columnList = "impact_analysis_id"),
        @Index(name = "idx_impact_comparison_tool", columnList = "tool"),
        @Index(name = "idx_impact_comparison_trend", columnList = "trend")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_impact_comparison_analysis_tool", columnNames = {"impact_analysis_id", "tool"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpactComparisonEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "impact_analysis_id", nullable = false)
    private ImpactAnalysisEntity impactAnalysis;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool", nullable = false)
    private QualityGateTool tool;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_category")
    private QualityGateCategory toolCategory;

    @Column(name = "introduced_at", nullable = false)
    private Instant introducedAt;

    @Column(name = "introduced_commit_sha", length = 40)
    private String introducedCommitSha;

    @Column(name = "samples_before")
    @Builder.Default
    private Integer samplesBefore = 0;

    @Column(name = "samples_after")
    @Builder.Default
    private Integer samplesAfter = 0;

    @Column(name = "improvement_score")
    private Double improvementScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "trend", nullable = false)
    private ImpactTrend trend;

    /**
     * Per-metric {@code {avgBefore, avgAfter, medianBefore, medianAfter, deltaAbsolute,
     * deltaPercent, trend}}, keyed by metric name (bugs, coverage, ...).
     */
    @Type(JsonType.class)
    @Column(name = "metrics_comparison", columnDefinition = "jsonb", nullable = false)
    private Object metricsComparison;
}
