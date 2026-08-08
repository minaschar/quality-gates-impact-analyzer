package com.thesis.qualitygateanalyzer.service.qualitymetrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.qualitygateanalyzer.dto.response.BulkIngestSummary;
import com.thesis.qualitygateanalyzer.dto.response.QualityMetricSnapshotDto;
import com.thesis.qualitygateanalyzer.dto.response.QualityMetricsResponse;
import com.thesis.qualitygateanalyzer.entity.qualitymetrics.QualityMetricSnapshotEntity;
import com.thesis.qualitygateanalyzer.exception.NoDataInDatasetException;
import com.thesis.qualitygateanalyzer.mapper.QualityMetricSnapshotMapper;
import com.thesis.qualitygateanalyzer.repository.qualitymetrics.QualityMetricSnapshotRepository;
import com.thesis.qualitygateanalyzer.service.qualitymetrics.QualityMetricsCsvLoader.RepoKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Default {@link QualityMetricsIngestionService} implementation.
 * Follows the same cache semantics as /analyze: forceNewAnalysis=false with existing data
 * returns the cached rows; otherwise a fresh ingestion is performed (and persisted) from
 * the dataset.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityMetricsIngestionServiceImpl implements QualityMetricsIngestionService {

    private static final DateTimeFormatter SOURCE_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS XX");

    private final QualityMetricsCsvLoader csvLoader;
    private final QualityMetricSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;
    private final QualityMetricSnapshotMapper snapshotMapper;

    @Override
    @Transactional
    public IngestResult ingest(String owner, String repo, boolean forceNewAnalysis) {
        if (!forceNewAnalysis && snapshotRepository.existsByOwnerAndRepo(owner, repo)) {
            log.info("Returning cached quality metrics for {}/{}", owner, repo);
            return new IngestResult(buildResponse(owner, repo), true);
        }

        List<CSVRecord> rows = csvLoader.loadRowsForRepository(owner, repo);
        if (rows.isEmpty()) {
            throw new NoDataInDatasetException(
                    "No software quality metrics found in dataset for " + owner + "/" + repo);
        }

        if (forceNewAnalysis) {
            log.info("Force refresh: clearing cached quality metrics for {}/{}", owner, repo);
            snapshotRepository.deleteByOwnerAndRepo(owner, repo);
        }

        List<QualityMetricSnapshotEntity> entities = rows.stream()
                .map(r -> toEntity(owner, repo, r))
                .toList();
        snapshotRepository.saveAll(entities);
        log.info("Stored {} quality metric rows for {}/{}", entities.size(), owner, repo);

        return new IngestResult(buildResponse(owner, repo), false);
    }

    @Override
    @Transactional(readOnly = true)
    public QualityMetricsResponse getStored(String owner, String repo) {
        return buildResponse(owner, repo);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasStoredMetrics(String owner, String repo) {
        return snapshotRepository.existsByOwnerAndRepo(owner, repo);
    }

    @Override
    @Transactional
    public BulkIngestSummary bulkIngest(boolean forceNewAnalysis) {
        Map<RepoKey, List<CSVRecord>> grouped = csvLoader.loadAllGroupedByRepository();

        int ingested = 0;
        int fromCache = 0;
        int totalRecords = 0;

        for (Map.Entry<RepoKey, List<CSVRecord>> entry : grouped.entrySet()) {
            String owner = entry.getKey().owner();
            String repo = entry.getKey().repo();

            if (!forceNewAnalysis && snapshotRepository.existsByOwnerAndRepo(owner, repo)) {
                fromCache++;
                continue;
            }

            if (forceNewAnalysis) {
                snapshotRepository.deleteByOwnerAndRepo(owner, repo);
            }

            List<QualityMetricSnapshotEntity> entities = entry.getValue().stream()
                    .map(r -> toEntity(owner, repo, r))
                    .toList();
            snapshotRepository.saveAll(entities);

            ingested++;
            totalRecords += entities.size();
        }

        log.info("Bulk ingest complete: {} repositories in dataset, {} ingested, {} from cache, {} records stored",
                grouped.size(), ingested, fromCache, totalRecords);

        return BulkIngestSummary.builder()
                .totalRepositoriesInDataset(grouped.size())
                .repositoriesIngested(ingested)
                .repositoriesFromCache(fromCache)
                .totalRecordsIngested(totalRecords)
                .build();
    }

    private QualityMetricsResponse buildResponse(String owner, String repo) {
        List<QualityMetricSnapshotDto> dtos = snapshotRepository
                .findByOwnerAndRepoOrderByPullRequestNumberAscAnalysisRoleAsc(owner, repo)
                .stream()
                .map(snapshotMapper::toDto)
                .toList();

        return QualityMetricsResponse.builder()
                .organization(owner)
                .repo(repo)
                .totalRecords(dtos.size())
                .records(dtos)
                .build();
    }

    private QualityMetricSnapshotEntity toEntity(String owner, String repo, CSVRecord r) {
        return QualityMetricSnapshotEntity.builder()
                .owner(owner)
                .repo(repo)
                .repositoryUrl(field(r, "repositoryUrl"))
                .pullRequestNumber(parseInt(field(r, "pullRequestNumber")))
                .pullRequestTitle(field(r, "pullRequestTitle"))
                .pullRequestUrl(field(r, "pullRequestUrl"))
                .pullRequestState(field(r, "pullRequestState"))
                .pullRequestMerged(parseBoolean(field(r, "pullRequestMerged")))
                .baseBranchName(field(r, "baseBranchName"))
                .headBranchName(field(r, "headBranchName"))
                .analysisRole(field(r, "analysisRole"))
                .commitHash(field(r, "commitHash"))
                .firstPullRequestCommitHash(field(r, "firstPullRequestCommitHash"))
                .closingCommitHash(field(r, "closingCommitHash"))
                .sonarProjectKey(field(r, "sonarProjectKey"))
                .sonarProjectName(field(r, "sonarProjectName"))
                .sonarDashboardUrl(field(r, "sonarDashboardUrl"))
                .sonarTaskId(field(r, "sonarTaskId"))
                .sonarAnalysisId(field(r, "sonarAnalysisId"))
                .qualityGateStatus(field(r, "qualityGateStatus"))
                .bugs(parseInt(field(r, "bugs")))
                .vulnerabilities(parseInt(field(r, "vulnerabilities")))
                .codeSmells(parseInt(field(r, "codeSmells")))
                .securityHotspots(parseInt(field(r, "securityHotspots")))
                .coverage(parseDouble(field(r, "coverage")))
                .duplicatedLinesDensity(parseDouble(field(r, "duplicatedLinesDensity")))
                .ncloc(parseInt(field(r, "ncloc")))
                .complexity(parseInt(field(r, "complexity")))
                .cognitiveComplexity(parseInt(field(r, "cognitiveComplexity")))
                .softwareQualityReliabilityIssues(parseInt(field(r, "softwareQualityReliabilityIssues")))
                .softwareQualityMaintainabilityIssues(parseInt(field(r, "softwareQualityMaintainabilityIssues")))
                .softwareQualitySecurityIssues(parseInt(field(r, "softwareQualitySecurityIssues")))
                .issuesSummary(parseJson(field(r, "issuesSummaryJson")))
                .categories(field(r, "Category"))
                .sourceRowId(parseLong(field(r, "id")))
                .sourceCreatedAt(parseSourceTimestamp(field(r, "createdAt")))
                .sourceUpdatedAt(parseSourceTimestamp(field(r, "updatedAt")))
                .build();
    }

    private String field(CSVRecord r, String name) {
        if (!r.isMapped(name)) {
            return null;
        }
        String v = r.get(name);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private Integer parseInt(String v) {
        if (v == null) return null;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String v) {
        if (v == null) return null;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String v) {
        if (v == null) return null;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean parseBoolean(String v) {
        return v == null ? null : Boolean.parseBoolean(v);
    }

    private Object parseJson(String v) {
        if (v == null) return null;
        try {
            return objectMapper.readValue(v, Map.class);
        } catch (Exception e) {
            log.debug("Failed to parse issuesSummaryJson: {}", e.getMessage());
            return null;
        }
    }

    private Instant parseSourceTimestamp(String v) {
        if (v == null) return null;
        try {
            return OffsetDateTime.parse(v, SOURCE_TIMESTAMP_FORMAT).toInstant();
        } catch (Exception e) {
            log.debug("Failed to parse source timestamp '{}': {}", v, e.getMessage());
            return null;
        }
    }
}
