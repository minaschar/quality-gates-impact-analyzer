package com.thesis.qualitygateanalyzer.mapper;

import com.thesis.qualitygateanalyzer.dto.response.QualityMetricSnapshotDto;
import com.thesis.qualitygateanalyzer.entity.qualitymetrics.QualityMetricSnapshotEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class QualityMetricSnapshotMapperImplTest {

    private final QualityMetricSnapshotMapperImpl mapper = new QualityMetricSnapshotMapperImpl();

    @Test
    void toDto_mapsEveryFieldOneToOne() {
        QualityMetricSnapshotEntity entity = QualityMetricSnapshotEntity.builder()
                .owner("octocat").repo("hello-world")
                .pullRequestNumber(5).pullRequestTitle("Fix bug").analysisRole("pr_base")
                .bugs(3).coverage(87.5).sourceRowId(100L)
                .sourceCreatedAt(Instant.parse("2024-01-01T00:00:00Z"))
                .build();

        QualityMetricSnapshotDto dto = mapper.toDto(entity);

        assertThat(dto.getPullRequestNumber()).isEqualTo(5);
        assertThat(dto.getPullRequestTitle()).isEqualTo("Fix bug");
        assertThat(dto.getAnalysisRole()).isEqualTo("pr_base");
        assertThat(dto.getBugs()).isEqualTo(3);
        assertThat(dto.getCoverage()).isEqualTo(87.5);
        assertThat(dto.getSourceRowId()).isEqualTo(100L);
        assertThat(dto.getSourceCreatedAt()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    void toDto_nullInput_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }
}
