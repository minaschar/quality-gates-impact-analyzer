package com.thesis.qualitygateanalyzer.entity.impactanalysis;

import com.thesis.qualitygateanalyzer.domain.enums.ImpactTrend;
import com.thesis.qualitygateanalyzer.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Main record of an E2E quality impact analysis for a repository: whether code quality
 * (from the ingested SonarQube metrics dataset) improved after its quality gate tool(s)
 * were introduced. Independent of {@code t_repositories} and keyed by (owner, repo)
 * directly, mirroring {@code CommitHistoryEntity} / {@code QualityMetricSnapshotEntity}.
 */
@Entity
@Table(name = "t_impact_analysis", indexes = {
        @Index(name = "idx_impact_analysis_owner_repo", columnList = "owner, repo"),
        @Index(name = "idx_impact_analysis_computed_at", columnList = "computed_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_impact_analysis_owner_repo", columnNames = {"owner", "repo"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpactAnalysisEntity extends BaseEntity {

    @Column(name = "owner", nullable = false)
    private String owner;

    @Column(name = "repo", nullable = false)
    private String repo;

    @Column(name = "repository_url", length = 500)
    private String repositoryUrl;

    @Column(name = "tools_analyzed_count")
    @Builder.Default
    private Integer toolsAnalyzedCount = 0;

    @Column(name = "overall_improvement_score")
    private Double overallImprovementScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_trend", nullable = false)
    private ImpactTrend overallTrend;

    @Column(name = "total_snapshots_before")
    @Builder.Default
    private Integer totalSnapshotsBefore = 0;

    @Column(name = "total_snapshots_after")
    @Builder.Default
    private Integer totalSnapshotsAfter = 0;

    @Column(name = "date_range_start")
    private Instant dateRangeStart;

    @Column(name = "date_range_end")
    private Instant dateRangeEnd;

    @Column(name = "analysis_time_ms")
    private Long analysisTimeMs;

    @Column(name = "api_calls_made")
    private Integer apiCallsMade;

    @Column(name = "computed_at")
    private Instant computedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "impactAnalysis", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ImpactMetricsSnapshotEntity> snapshots = new ArrayList<>();

    @OneToMany(mappedBy = "impactAnalysis", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ImpactComparisonEntity> comparisons = new ArrayList<>();

    public void addSnapshot(ImpactMetricsSnapshotEntity snapshot) {
        snapshots.add(snapshot);
        snapshot.setImpactAnalysis(this);
    }

    public void addComparison(ImpactComparisonEntity comparison) {
        comparisons.add(comparison);
        comparison.setImpactAnalysis(this);
    }

    public String getFullName() {
        return owner + "/" + repo;
    }
}
