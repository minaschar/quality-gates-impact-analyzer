package com.thesis.qualitygateanalyzer.entity.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.PROutcome;
import com.thesis.qualitygateanalyzer.entity.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity for storing sample PRs analyzed for enforcement.
 */
@Entity
@Table(name = "t_pr_samples", indexes = {
        @Index(name = "idx_pr_samples_enforcement", columnList = "enforcement_id"),
        @Index(name = "idx_pr_samples_outcome", columnList = "outcome"),
        @Index(name = "idx_pr_samples_pr_number", columnList = "pr_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PRSampleEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enforcement_id", nullable = false)
    private EnforcementEntity enforcement;

    // PR INFO

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "pr_title", columnDefinition = "TEXT")
    private String prTitle;

    @Column(name = "pr_url", length = 500)
    private String prUrl;

    @Column(name = "state", length = 50)
    private String state;

    @Column(name = "merged")
    @Builder.Default
    private Boolean merged = false;

    // TIMESTAMPS

    @Column(name = "pr_created_at")
    private Instant prCreatedAt;

    @Column(name = "pr_closed_at")
    private Instant prClosedAt;

    @Column(name = "pr_merged_at")
    private Instant prMergedAt;

    // WORKFLOW ANALYSIS

    @Column(name = "had_workflow_failure")
    @Builder.Default
    private Boolean hadWorkflowFailure = false;

    @Column(name = "last_workflow_passed")
    private Boolean lastWorkflowPassed;

    // QG CHECK ANALYSIS

    @Column(name = "had_verified_qg_failure")
    @Builder.Default
    private Boolean hadVerifiedQgFailure = false;

    @Column(name = "last_qg_check_passed")
    private Boolean lastQgCheckPassed;

    @Type(JsonType.class)
    @Column(name = "failed_qg_tools", columnDefinition = "jsonb")
    private List<String> failedQgTools;  // Tool names as strings

    @Type(JsonType.class)
    @Column(name = "failure_messages", columnDefinition = "jsonb")
    private List<String> failureMessages;

    // OUTCOME

    @Column(name = "qg_was_required_check")
    @Builder.Default
    private Boolean qgWasRequiredCheck = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome")
    private PROutcome outcome;

    @Column(name = "outcome_reason", columnDefinition = "TEXT")
    private String outcomeReason;

    // RELATIONSHIPS (detailed runs and checks)

    @OneToMany(mappedBy = "prSample", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PRWorkflowRunEntity> workflowRuns = new ArrayList<>();

    @OneToMany(mappedBy = "prSample", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PRCheckRunEntity> checkRuns = new ArrayList<>();

    @OneToMany(mappedBy = "prSample", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PRCommitStatusEntity> commitStatuses = new ArrayList<>();

    // HELPER METHODS

    public void addWorkflowRun(PRWorkflowRunEntity run) {
        workflowRuns.add(run);
        run.setPrSample(this);
    }

    public void addCheckRun(PRCheckRunEntity checkRun) {
        checkRuns.add(checkRun);
        checkRun.setPrSample(this);
    }

    public void addCommitStatus(PRCommitStatusEntity status) {
        commitStatuses.add(status);
        status.setPrSample(this);
    }
}
