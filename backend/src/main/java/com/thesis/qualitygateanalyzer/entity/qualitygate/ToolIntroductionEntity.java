package com.thesis.qualitygateanalyzer.entity.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateCategory;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity for storing when each quality gate tool was introduced in the repository.
 */
@Entity
@Table(name = "t_tool_introductions", indexes = {
        @Index(name = "idx_tool_introductions_repository", columnList = "repository_id"),
        @Index(name = "idx_tool_introductions_tool", columnList = "tool"),
        @Index(name = "idx_tool_introductions_date", columnList = "effective_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolIntroductionEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryEntity repository;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool", nullable = false)
    private QualityGateTool tool;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private QualityGateCategory category;

    // EFFECTIVE INTRODUCTION (Computed earliest)

    @Column(name = "effective_sha", length = 40)
    private String effectiveSha;

    @Column(name = "effective_short_sha", length = 7)
    private String effectiveShortSha;

    @Column(name = "effective_date")
    private Instant effectiveDate;

    @Column(name = "author")
    private String author;

    @Column(name = "commit_message", columnDefinition = "TEXT")
    private String commitMessage;

    // DETECTION METHOD

    @Column(name = "detection_method", length = 100)
    private String detectionMethod;  // e.g., "graphql-blame", "pr-fallback", "file-creation"

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "search_pattern", length = 500)
    private String searchPattern;

    // STATUS FLAGS

    @Column(name = "present_since_creation")
    @Builder.Default
    private Boolean presentSinceCreation = false;

    @Column(name = "introduction_summary", columnDefinition = "TEXT")
    private String introductionSummary;

    // FILE INTRODUCTIONS (detailed per-file history)

    @OneToMany(mappedBy = "toolIntroduction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<FileIntroductionEntity> fileIntroductions = new ArrayList<>();

    public void addFileIntroduction(FileIntroductionEntity fileIntro) {
        fileIntroductions.add(fileIntro);
        fileIntro.setToolIntroduction(this);
    }
}
