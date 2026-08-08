package com.thesis.qualitygateanalyzer.entity.qualitygate;

import com.thesis.qualitygateanalyzer.entity.BaseEntity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.List;

/**
 * Entity for storing workflows that contain quality gate tools.
 */
@Entity
@Table(name = "t_qg_workflows", indexes = {
        @Index(name = "idx_qg_workflows_repository", columnList = "repository_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QGWorkflowEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryEntity repository;

    @Column(name = "workflow_file", nullable = false, length = 500)
    private String workflowFile;

    @Column(name = "workflow_name")
    private String workflowName;

    @Type(JsonType.class)
    @Column(name = "tools", columnDefinition = "jsonb")
    private List<String> tools;  // Tool names as strings

    @Column(name = "triggers_on_pr")
    @Builder.Default
    private Boolean triggersOnPR = false;

    @Type(JsonType.class)
    @Column(name = "build_commands", columnDefinition = "jsonb")
    private List<String> buildCommands;
}
