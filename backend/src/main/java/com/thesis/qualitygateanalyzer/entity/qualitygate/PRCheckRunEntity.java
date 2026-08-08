package com.thesis.qualitygateanalyzer.entity.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity for storing check runs associated with a PR sample.
 */
@Entity
@Table(name = "t_pr_check_runs", indexes = {
        @Index(name = "idx_pr_check_runs_pr", columnList = "pr_sample_id"),
        @Index(name = "idx_pr_check_runs_matched_tool", columnList = "matched_tool")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PRCheckRunEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pr_sample_id", nullable = false)
    private PRSampleEntity prSample;

    @Column(name = "check_run_id", nullable = false)
    private Long checkRunId;

    @Column(name = "name", length = 500)
    private String name;

    @Column(name = "app_slug")
    private String appSlug;

    @Column(name = "conclusion", length = 50)
    private String conclusion;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "output_title", columnDefinition = "TEXT")
    private String outputTitle;

    @Column(name = "output_summary", columnDefinition = "TEXT")
    private String outputSummary;

    @Column(name = "head_sha", length = 40)
    private String headSha;

    @Column(name = "completed_at")
    private Instant completedAt;

    // MATCHED TOOL INFO

    @Enumerated(EnumType.STRING)
    @Column(name = "matched_tool")
    private QualityGateTool matchedTool;

    @Column(name = "match_confidence")
    private Double matchConfidence;

    /**
     * Check if this check run failed.
     */
    public boolean isFailed() {
        return "failure".equalsIgnoreCase(conclusion) ||
                "action_required".equalsIgnoreCase(conclusion);
    }

    /**
     * Check if this check run succeeded.
     */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(conclusion);
    }

    /**
     * Check if this is a QG-related check.
     */
    public boolean isQualityGateRelated() {
        return matchedTool != null;
    }
}
