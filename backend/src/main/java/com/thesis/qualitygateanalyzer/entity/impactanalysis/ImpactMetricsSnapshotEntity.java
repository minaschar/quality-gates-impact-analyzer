package com.thesis.qualitygateanalyzer.entity.impactanalysis;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A single quality-metric snapshot (one row of the ingested SonarQube dataset) classified
 * as BEFORE or AFTER a specific quality gate tool's introduction date. Self-contained copy
 * of the handful of metric columns needed here rather than an FK to
 * {@code QualityMetricSnapshotEntity} -- see {@code V6__impact_analysis.sql} for why.
 * <p>
 * The same underlying commit can appear once per relevant tool, since tools are typically
 * introduced at different times.
 */
@Entity
@Table(name = "t_impact_metrics_snapshot", indexes = {
        @Index(name = "idx_impact_snapshot_analysis", columnList = "impact_analysis_id"),
        @Index(name = "idx_impact_snapshot_tool", columnList = "impact_analysis_id, tool"),
        @Index(name = "idx_impact_snapshot_tool_class", columnList = "impact_analysis_id, tool, classification"),
        @Index(name = "idx_impact_snapshot_commit_date", columnList = "commit_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpactMetricsSnapshotEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "impact_analysis_id", nullable = false)
    private ImpactAnalysisEntity impactAnalysis;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool", nullable = false)
    private QualityGateTool tool;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false)
    private Classification classification;

    @Column(name = "pull_request_number")
    private Integer pullRequestNumber;

    @Column(name = "analysis_role", length = 20)
    private String analysisRole;

    @Column(name = "commit_sha", length = 40)
    private String commitSha;

    @Column(name = "commit_date", nullable = false)
    private Instant commitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_source", length = 30)
    private DateSource dateSource;

    @Column(name = "bugs")
    private Integer bugs;

    @Column(name = "vulnerabilities")
    private Integer vulnerabilities;

    @Column(name = "code_smells")
    private Integer codeSmells;

    @Column(name = "security_hotspots")
    private Integer securityHotspots;

    @Column(name = "coverage")
    private Double coverage;

    @Column(name = "duplicated_lines_density")
    private Double duplicatedLinesDensity;

    @Column(name = "ncloc")
    private Integer ncloc;

    @Column(name = "complexity")
    private Integer complexity;

    @Column(name = "cognitive_complexity")
    private Integer cognitiveComplexity;

    @Column(name = "software_quality_reliability_issues")
    private Integer softwareQualityReliabilityIssues;

    @Column(name = "software_quality_maintainability_issues")
    private Integer softwareQualityMaintainabilityIssues;

    @Column(name = "software_quality_security_issues")
    private Integer softwareQualitySecurityIssues;

    public enum Classification {
        BEFORE, AFTER
    }

    /**
     * How {@link #commitDate} was resolved, since the source dataset has no date column.
     */
    public enum DateSource {
        COMMIT_HISTORY, PR_METADATA
    }
}
