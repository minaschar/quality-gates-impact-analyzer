package com.thesis.qualitygateanalyzer.entity.qualitygate;

import com.thesis.qualitygateanalyzer.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity for storing metadata about the history detection process.
 * One-to-one relationship with RepositoryEntity.
 */
@Entity
@Table(name = "t_history_analysis_metadata", indexes = {
        @Index(name = "idx_history_metadata_repository", columnList = "repository_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoryDetectionMetadataEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false, unique = true)
    private RepositoryEntity repository;

    @Column(name = "method", length = 100)
    private String method;  // e.g., "graphql-blame+pr-fallback"

    @Column(name = "files_analyzed")
    @Builder.Default
    private Integer filesScanned = 0;

    @Column(name = "tools_analyzed")
    @Builder.Default
    private Integer toolsScanned = 0;

    @Column(name = "analysis_duration_ms")
    private Long detectionDurationMs;

    @Column(name = "success")
    @Builder.Default
    private Boolean success = true;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
