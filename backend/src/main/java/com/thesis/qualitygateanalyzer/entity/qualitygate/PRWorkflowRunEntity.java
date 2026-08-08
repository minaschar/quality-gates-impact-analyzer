package com.thesis.qualitygateanalyzer.entity.qualitygate;

import com.thesis.qualitygateanalyzer.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity for storing workflow runs associated with a PR sample.
 */
@Entity
@Table(name = "t_pr_workflow_runs", indexes = {
        @Index(name = "idx_pr_workflow_runs_pr", columnList = "pr_sample_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PRWorkflowRunEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pr_sample_id", nullable = false)
    private PRSampleEntity prSample;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "workflow_file", length = 500)
    private String workflowFile;

    @Column(name = "workflow_name")
    private String workflowName;

    @Column(name = "head_sha", length = 40)
    private String headSha;

    @Column(name = "conclusion", length = 50)
    private String conclusion;  // success, failure, cancelled

    @Column(name = "status", length = 50)
    private String status;  // completed, in_progress, queued

    @Column(name = "event", length = 50)
    private String event;  // push, pull_request

    @Column(name = "html_url", length = 500)
    private String htmlUrl;

    @Column(name = "run_created_at")
    private Instant runCreatedAt;

    /**
     * Check if this run failed.
     */
    public boolean isFailed() {
        return "failure".equalsIgnoreCase(conclusion);
    }

    /**
     * Check if this run succeeded.
     */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(conclusion);
    }
}
