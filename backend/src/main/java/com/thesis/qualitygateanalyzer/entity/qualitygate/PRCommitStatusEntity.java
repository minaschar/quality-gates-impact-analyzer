package com.thesis.qualitygateanalyzer.entity.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity for storing commit statuses (from external apps) associated with a PR sample.
 */
@Entity
@Table(name = "t_pr_commit_statuses", indexes = {
        @Index(name = "idx_pr_commit_statuses_pr", columnList = "pr_sample_id"),
        @Index(name = "idx_pr_commit_statuses_matched_tool", columnList = "matched_tool")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PRCommitStatusEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pr_sample_id", nullable = false)
    private PRSampleEntity prSample;

    @Column(name = "status_id")
    private Long statusId;

    @Column(name = "state", length = 50)
    private String state;  // error, failure, pending, success

    @Column(name = "context", length = 500)
    private String context;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_url", length = 1000)
    private String targetUrl;

    @Column(name = "status_created_at")
    private Instant statusCreatedAt;

    @Column(name = "status_updated_at")
    private Instant statusUpdatedAt;

    // MATCHED TOOL INFO

    @Enumerated(EnumType.STRING)
    @Column(name = "matched_tool")
    private QualityGateTool matchedTool;

    @Column(name = "match_confidence")
    private Double matchConfidence;

    /**
     * Check if this status indicates failure.
     */
    public boolean isFailed() {
        return "failure".equalsIgnoreCase(state) || "error".equalsIgnoreCase(state);
    }

    /**
     * Check if this status indicates success.
     */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(state);
    }

    /**
     * Check if this is a QG-related status.
     */
    public boolean isQualityGateRelated() {
        return matchedTool != null;
    }
}
