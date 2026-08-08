package com.thesis.qualitygateanalyzer.entity.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.EnforcementStatus;
import com.thesis.qualitygateanalyzer.entity.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity for storing enforcement detection results.
 * One-to-one relationship with RepositoryEntity.
 */
@Entity
@Table(name = "t_enforcement", indexes = {
        @Index(name = "idx_enforcement_repository", columnList = "repository_id"),
        @Index(name = "idx_enforcement_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnforcementEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false, unique = true)
    private RepositoryEntity repository;

    // MAIN METRICS

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EnforcementStatus status;

    @Column(name = "score")
    private Double score;

    @Column(name = "confidence", length = 20)
    private String confidence;  // HIGH, MEDIUM, LOW

    // PR STATISTICS

    @Column(name = "total_prs_analyzed")
    @Builder.Default
    private Integer totalPrsChecked = 0;

    @Column(name = "prs_with_qg_failures")
    @Builder.Default
    private Integer prsWithQgFailures = 0;

    @Column(name = "fixed_then_merged")
    @Builder.Default
    private Integer fixedThenMerged = 0;

    @Column(name = "blocked")
    @Builder.Default
    private Integer blocked = 0;

    @Column(name = "merged_with_failure")
    @Builder.Default
    private Integer mergedWithFailure = 0;

    @Column(name = "still_open")
    @Builder.Default
    private Integer stillOpen = 0;

    // ADDITIONAL INFO (JSONB)

    @Type(JsonType.class)
    @Column(name = "branch_protection_info", columnDefinition = "jsonb")
    private Object branchProtectionInfo;

    @Type(JsonType.class)
    @Column(name = "fallback_info", columnDefinition = "jsonb")
    private Object fallbackInfo;

    @Column(name = "interpretation", columnDefinition = "TEXT")
    private String interpretation;

    // RELATIONSHIPS

    @OneToMany(mappedBy = "enforcement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<EnforcementByToolEntity> byTool = new ArrayList<>();

    @OneToMany(mappedBy = "enforcement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PRSampleEntity> samplePRs = new ArrayList<>();

    // HELPER METHODS

    public void addByTool(EnforcementByToolEntity toolStats) {
        byTool.add(toolStats);
        toolStats.setEnforcement(this);
    }

    public void addSamplePR(PRSampleEntity prSample) {
        samplePRs.add(prSample);
        prSample.setEnforcement(this);
    }
}
