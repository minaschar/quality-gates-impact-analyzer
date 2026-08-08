package com.thesis.qualitygateanalyzer.entity.qualitymetrics;

import com.thesis.qualitygateanalyzer.entity.BaseEntity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;

/**
 * One row per PR-analysis commit (pr_base or pr_closed) ingested from the
 * software quality metrics dataset. Independent of t_repositories, mirroring
 * CommitHistoryEntity: metrics can be ingested without a prior /analyze run.
 */
@Entity
@Table(name = "t_quality_metric_snapshots", indexes = {
        @Index(name = "idx_qms_owner_repo", columnList = "owner, repo"),
        @Index(name = "idx_qms_owner_repo_commit", columnList = "owner, repo, commit_hash"),
        @Index(name = "idx_qms_owner_repo_pr", columnList = "owner, repo, pull_request_number"),
        @Index(name = "idx_qms_role", columnList = "analysis_role")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_quality_metric_snapshots_source_row", columnNames = "source_row_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QualityMetricSnapshotEntity extends BaseEntity {

    @Column(name = "owner", nullable = false)
    private String owner;

    @Column(name = "repo", nullable = false)
    private String repo;

    @Column(name = "repository_url", length = 500)
    private String repositoryUrl;

    @Column(name = "pull_request_number")
    private Integer pullRequestNumber;

    @Column(name = "pull_request_title", columnDefinition = "TEXT")
    private String pullRequestTitle;

    @Column(name = "pull_request_url", length = 500)
    private String pullRequestUrl;

    @Column(name = "pull_request_state", length = 50)
    private String pullRequestState;

    @Column(name = "pull_request_merged")
    private Boolean pullRequestMerged;

    @Column(name = "base_branch_name")
    private String baseBranchName;

    @Column(name = "head_branch_name")
    private String headBranchName;

    @Column(name = "analysis_role", nullable = false, length = 50)
    private String analysisRole;  // 'pr_base' or 'pr_closed'

    @Column(name = "commit_hash", length = 40)
    private String commitHash;

    @Column(name = "first_pull_request_commit_hash", length = 40)
    private String firstPullRequestCommitHash;

    @Column(name = "closing_commit_hash", length = 40)
    private String closingCommitHash;

    @Column(name = "sonar_project_key", length = 500)
    private String sonarProjectKey;

    @Column(name = "sonar_project_name", length = 500)
    private String sonarProjectName;

    @Column(name = "sonar_dashboard_url", length = 1000)
    private String sonarDashboardUrl;

    @Column(name = "sonar_task_id", length = 100)
    private String sonarTaskId;

    @Column(name = "sonar_analysis_id", length = 100)
    private String sonarAnalysisId;

    @Column(name = "quality_gate_status", length = 20)
    private String qualityGateStatus;  // Sonar's own gate: 'OK' or 'ERROR'

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

    @Type(JsonType.class)
    @Column(name = "issues_summary", columnDefinition = "jsonb")
    private Object issuesSummary;

    @Column(name = "categories", length = 500)
    private String categories;  // raw "Category" tag list from the dataset

    @Column(name = "source_row_id")
    private Long sourceRowId;  // "id" column from the source dataset, for traceability

    @Column(name = "source_created_at")
    private Instant sourceCreatedAt;

    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;
}
