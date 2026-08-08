package com.thesis.qualitygateanalyzer.entity.qualitygate;

import com.thesis.qualitygateanalyzer.entity.BaseEntity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;

/**
 * Entity for storing detailed file-level introduction history.
 * Each tool introduction can have multiple file introductions (config files, CI files).
 */
@Entity
@Table(name = "t_file_introductions", indexes = {
        @Index(name = "idx_file_introductions_tool", columnList = "tool_introduction_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileIntroductionEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_introduction_id", nullable = false)
    private ToolIntroductionEntity toolIntroduction;

    @Column(name = "introduction_type", nullable = false, length = 50)
    private String introductionType;  // 'CONFIG' or 'CI'

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "search_pattern", length = 500)
    private String searchPattern;

    // INTRODUCTION COMMIT

    @Column(name = "introduced_sha", length = 40)
    private String introducedSha;

    @Column(name = "introduced_short_sha", length = 7)
    private String introducedShortSha;

    @Column(name = "introduced_date")
    private Instant introducedDate;

    @Column(name = "introduced_author")
    private String introducedAuthor;

    @Column(name = "introduced_message", columnDefinition = "TEXT")
    private String introducedMessage;

    @Column(name = "present_since_file_creation")
    @Builder.Default
    private Boolean presentSinceFileCreation = false;

    @Type(JsonType.class)
    @Column(name = "all_occurrences", columnDefinition = "jsonb")
    private List<Object> allOccurrences;  // Array of commit info objects
}
