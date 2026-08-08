package com.thesis.qualitygateanalyzer.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.qualitygateanalyzer.domain.enums.ImpactTrend;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.dto.response.MetricComparisonDto;
import com.thesis.qualitygateanalyzer.dto.response.TimelinePointDto;
import com.thesis.qualitygateanalyzer.dto.response.ToolComparisonDto;
import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactComparisonEntity;
import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactMetricsSnapshotEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImpactAnalysisMapperImplTest {

    private ImpactAnalysisMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new ImpactAnalysisMapperImpl();
        mapper.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void toDto_snapshot_mapsClassificationEnumToString() {
        ImpactMetricsSnapshotEntity entity = ImpactMetricsSnapshotEntity.builder()
                .tool(QualityGateTool.CHECKSTYLE)
                .classification(ImpactMetricsSnapshotEntity.Classification.BEFORE)
                .commitSha("abc123")
                .commitDate(Instant.parse("2024-01-01T00:00:00Z"))
                .pullRequestNumber(7)
                .bugs(3)
                .coverage(80.0)
                .build();

        TimelinePointDto dto = mapper.toDto(entity);

        assertThat(dto.getTool()).isEqualTo(QualityGateTool.CHECKSTYLE);
        assertThat(dto.getClassification()).isEqualTo("BEFORE");
        assertThat(dto.getCommitSha()).isEqualTo("abc123");
        assertThat(dto.getPullRequestNumber()).isEqualTo(7);
        assertThat(dto.getBugs()).isEqualTo(3);
        assertThat(dto.getCoverage()).isEqualTo(80.0);
    }

    @Test
    void toDto_comparison_deserializesJsonbMetricsMap() {
        Map<String, Object> rawJson = new LinkedHashMap<>();
        rawJson.put("bugs", Map.of(
                "avgBefore", 10.0, "avgAfter", 2.0, "medianBefore", 10.0, "medianAfter", 2.0,
                "deltaAbsolute", -8.0, "deltaPercent", -80.0, "trend", "IMPROVED"));

        ImpactComparisonEntity entity = ImpactComparisonEntity.builder()
                .tool(QualityGateTool.CHECKSTYLE)
                .introducedAt(Instant.parse("2024-06-01T00:00:00Z"))
                .samplesBefore(1)
                .samplesAfter(1)
                .trend(ImpactTrend.IMPROVED)
                .metricsComparison(rawJson)
                .build();

        ToolComparisonDto dto = mapper.toDto(entity);

        assertThat(dto.getTool()).isEqualTo(QualityGateTool.CHECKSTYLE);
        assertThat(dto.getSamplesBefore()).isEqualTo(1);
        assertThat(dto.getTrend()).isEqualTo(ImpactTrend.IMPROVED);
        assertThat(dto.getMetrics()).containsKey("bugs");
        MetricComparisonDto bugs = dto.getMetrics().get("bugs");
        assertThat(bugs.getAvgBefore()).isEqualTo(10.0);
        assertThat(bugs.getAvgAfter()).isEqualTo(2.0);
        assertThat(bugs.getTrend()).isEqualTo(ImpactTrend.IMPROVED);
    }

    @Test
    void toDto_comparison_nullMetricsComparison_returnsEmptyMap() {
        ImpactComparisonEntity entity = ImpactComparisonEntity.builder()
                .tool(QualityGateTool.CHECKSTYLE)
                .introducedAt(Instant.parse("2024-06-01T00:00:00Z"))
                .trend(ImpactTrend.INSUFFICIENT_DATA)
                .metricsComparison(null)
                .build();

        ToolComparisonDto dto = mapper.toDto(entity);

        assertThat(dto.getMetrics()).isEmpty();
    }

    @Test
    void toDto_comparison_defaultsNullSampleCountsToZero() {
        ImpactComparisonEntity entity = ImpactComparisonEntity.builder()
                .tool(QualityGateTool.CHECKSTYLE)
                .introducedAt(Instant.parse("2024-06-01T00:00:00Z"))
                .trend(ImpactTrend.INSUFFICIENT_DATA)
                .samplesBefore(null)
                .samplesAfter(null)
                .metricsComparison(Map.of())
                .build();

        ToolComparisonDto dto = mapper.toDto(entity);

        assertThat(dto.getSamplesBefore()).isZero();
        assertThat(dto.getSamplesAfter()).isZero();
    }

    @Test
    void toTimelineDtos_mapsEachEntity() {
        ImpactMetricsSnapshotEntity a = ImpactMetricsSnapshotEntity.builder()
                .tool(QualityGateTool.CHECKSTYLE).classification(ImpactMetricsSnapshotEntity.Classification.BEFORE)
                .commitDate(Instant.parse("2024-01-01T00:00:00Z")).build();
        ImpactMetricsSnapshotEntity b = ImpactMetricsSnapshotEntity.builder()
                .tool(QualityGateTool.CHECKSTYLE).classification(ImpactMetricsSnapshotEntity.Classification.AFTER)
                .commitDate(Instant.parse("2024-12-01T00:00:00Z")).build();

        List<TimelinePointDto> dtos = mapper.toTimelineDtos(List.of(a, b));

        assertThat(dtos).hasSize(2);
    }
}
