package com.thesis.qualitygateanalyzer.entity.qualitygate;

import com.thesis.qualitygateanalyzer.entity.BaseEntity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Entity representing an analyzed GitHub repository.
 * This is the main entity that links to all detection results.
 */
@Entity
@Table(name = "t_repositories", indexes = {
        @Index(name = "idx_repositories_owner_repo", columnList = "owner, repo"),
        @Index(name = "idx_repositories_is_current", columnList = "is_current"),
        @Index(name = "idx_repositories_analyzed_at", columnList = "analyzed_at"),
        @Index(name = "idx_repositories_has_qg", columnList = "has_quality_gate")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_repositories_owner_repo_version",
                columnNames = {"owner", "repo", "analysis_version"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryEntity extends BaseEntity {

    // BASIC INFO

    @Column(name = "owner", nullable = false)
    private String owner;

    @Column(name = "repo", nullable = false)
    private String repo;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "primary_language", length = 100)
    private String primaryLanguage;

    @Column(name = "default_branch")
    private String defaultBranch;

    @Column(name = "stars")
    @Builder.Default
    private Integer stars = 0;

    @Column(name = "forks")
    @Builder.Default
    private Integer forks = 0;

    // DETECTION RESULTS

    @Column(name = "has_quality_gate")
    @Builder.Default
    private Boolean hasQualityGate = false;

    @Column(name = "recommendation", length = 100)
    private String recommendation;

    @Column(name = "conclusion", columnDefinition = "TEXT")
    private String conclusion;

    // DETECTION METADATA

    @Column(name = "analysis_version")
    @Builder.Default
    private Integer detectionVersion = 1;

    @Column(name = "is_current")
    @Builder.Default
    private Boolean isCurrent = true;

    @Column(name = "analysis_time_ms")
    private Long detectionTimeMs;

    @Column(name = "api_calls_made")
    private Integer apiCallsMade;

    @Column(name = "analyzed_at")
    private Instant detectedAt;

    // REPO HISTORY CONTEXT

    @Column(name = "first_commit_sha", length = 40)
    private String firstCommitSha;

    @Column(name = "first_commit_date")
    private Instant firstCommitDate;

    @Column(name = "total_commits")
    private Integer totalCommits;

    // BRANCH PROTECTION (JSONB)

    @Type(JsonType.class)
    @Column(name = "branch_protection", columnDefinition = "jsonb")
    private Object branchProtection;

    @Type(JsonType.class)
    @Column(name = "required_qg_tools", columnDefinition = "jsonb")
    private Set<String> requiredQgTools;

    @Type(JsonType.class)
    @Column(name = "informational_qg_tools", columnDefinition = "jsonb")
    private Set<String> informationalQgTools;

    // TIMESTAMPS

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // RELATIONSHIPS

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<QGDetectionEntity> detections = new ArrayList<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<QGWorkflowEntity> workflows = new ArrayList<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ToolIntroductionEntity> toolIntroductions = new ArrayList<>();

    @OneToOne(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private EnforcementEntity enforcement;

    @OneToOne(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private HistoryDetectionMetadataEntity historyMetadata;

    // HELPER METHODS

    public void addDetection(QGDetectionEntity detection) {
        detections.add(detection);
        detection.setRepository(this);
    }

    public void addWorkflow(QGWorkflowEntity workflow) {
        workflows.add(workflow);
        workflow.setRepository(this);
    }

    public void addToolIntroduction(ToolIntroductionEntity introduction) {
        toolIntroductions.add(introduction);
        introduction.setRepository(this);
    }

    public void setEnforcement(EnforcementEntity enforcement) {
        this.enforcement = enforcement;
        if (enforcement != null) {
            enforcement.setRepository(this);
        }
    }

    public void setHistoryMetadata(HistoryDetectionMetadataEntity metadata) {
        this.historyMetadata = metadata;
        if (metadata != null) {
            metadata.setRepository(this);
        }
    }

    /**
     * Full name of the repository (owner/repo).
     */
    public String getFullName() {
        return owner + "/" + repo;
    }
}
