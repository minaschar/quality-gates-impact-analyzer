package com.thesis.qualitygateanalyzer.entity.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entity for storing per-tool enforcement breakdown.
 */
@Entity
@Table(name = "t_enforcement_by_tool", indexes = {
        @Index(name = "idx_enforcement_by_tool_enforcement", columnList = "enforcement_id"),
        @Index(name = "idx_enforcement_by_tool_tool", columnList = "tool")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnforcementByToolEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enforcement_id", nullable = false)
    private EnforcementEntity enforcement;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool", nullable = false)
    private QualityGateTool tool;

    @Column(name = "failures")
    @Builder.Default
    private Integer failures = 0;

    @Column(name = "enforced")
    @Builder.Default
    private Integer enforced = 0;

    @Column(name = "bypassed")
    @Builder.Default
    private Integer bypassed = 0;

    @Column(name = "is_required_check")
    @Builder.Default
    private Boolean isRequiredCheck = false;

    @Column(name = "enforcement_rate")
    private Double enforcementRate;

    /**
     * Calculate enforcement rate.
     */
    public Double calculateEnforcementRate() {
        int total = enforced + bypassed;
        if (total == 0) return null;
        return (double) enforced / total;
    }
}
