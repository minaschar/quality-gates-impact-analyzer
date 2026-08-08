package com.thesis.qualitygateanalyzer.entity.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateCategory;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.entity.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.List;

/**
 * Entity for storing detected quality gate tools from Phase 1 detection.
 */
@Entity
@Table(name = "t_qg_detections", indexes = {
        @Index(name = "idx_qg_detections_repository", columnList = "repository_id"),
        @Index(name = "idx_qg_detections_tool", columnList = "tool"),
        @Index(name = "idx_qg_detections_category", columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QGDetectionEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryEntity repository;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool", nullable = false)
    private QualityGateTool tool;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private QualityGateCategory category;

    @Column(name = "source_file", length = 500)
    private String sourceFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Type(JsonType.class)
    @Column(name = "evidence_found", columnDefinition = "jsonb")
    private List<String> evidenceFound;

    @Column(name = "confidence_score")
    @Builder.Default
    private Double confidenceScore = 1.0;

    @Column(name = "triggers_on_pr")
    private Boolean triggersOnPR;

    @Column(name = "associated_workflow", length = 500)
    private String associatedWorkflow;

    @Column(name = "relevant_for_thesis")
    @Builder.Default
    private Boolean relevantForThesis = true;

    /**
     * Source types for QG detection.
     */
    public enum SourceType {
        WORKFLOW_ACTION,
        WORKFLOW_COMMAND,
        BUILD_TOOL,
        CONFIG_FILE,
        CHECK_RUN,
        COMMIT_STATUS
    }
}
