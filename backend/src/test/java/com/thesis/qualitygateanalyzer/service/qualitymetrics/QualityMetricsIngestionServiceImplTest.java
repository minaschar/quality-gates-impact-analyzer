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
import com.thesis.qualitygateanalyzer.service.qualitymetrics.QualityMetricsIngestionService.IngestResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QualityMetricsIngestionServiceImplTest {

    @Mock
    private QualityMetricsCsvLoader csvLoader;
    @Mock
    private QualityMetricSnapshotRepository snapshotRepository;
    @Mock
    private QualityMetricSnapshotMapper snapshotMapper;

    private QualityMetricsIngestionServiceImpl service;

    private static final String OWNER = "apache";
    private static final String REPO = "hello-world";

    @BeforeEach
    void setUp() {
        service = new QualityMetricsIngestionServiceImpl(csvLoader, snapshotRepository, new ObjectMapper(), snapshotMapper);
    }

    private List<CSVRecord> parseCsv(String csv) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
        try (CSVParser parser = format.parse(new StringReader(csv))) {
            return parser.getRecords();
        }
    }

    private void stubEmptyResponse() {
        when(snapshotRepository.findByOwnerAndRepoOrderByPullRequestNumberAscAnalysisRoleAsc(OWNER, REPO))
                .thenReturn(List.of());
    }

    @Nested
    class Ingest {
        @Test
        void cacheHit_returnsStoredResponseWithoutTouchingLoader() {
            when(snapshotRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);
            stubEmptyResponse();

            IngestResult result = service.ingest(OWNER, REPO, false);

            assertThat(result.fromCache()).isTrue();
            verifyNoInteractions(csvLoader);
        }

        @Test
        void cacheMiss_noRowsInDataset_throwsNoDataInDatasetException() {
            when(snapshotRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(false);
            when(csvLoader.loadRowsForRepository(OWNER, REPO)).thenReturn(List.of());

            assertThrows(NoDataInDatasetException.class, () -> service.ingest(OWNER, REPO, false));
        }

        @Test
        void cacheMiss_withRows_savesEntitiesAndReturnsFreshResult() throws IOException {
            when(snapshotRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(false);
            List<CSVRecord> rows = parseCsv(minimalValidCsv());
            when(csvLoader.loadRowsForRepository(OWNER, REPO)).thenReturn(rows);
            stubEmptyResponse();

            IngestResult result = service.ingest(OWNER, REPO, false);

            assertThat(result.fromCache()).isFalse();
            verify(snapshotRepository, never()).deleteByOwnerAndRepo(any(), any());
            verify(snapshotRepository).saveAll(anyList());
        }

        @Test
        void forceNewAnalysis_deletesExistingBeforeReingesting() throws IOException {
            List<CSVRecord> rows = parseCsv(minimalValidCsv());
            when(csvLoader.loadRowsForRepository(OWNER, REPO)).thenReturn(rows);
            stubEmptyResponse();

            IngestResult result = service.ingest(OWNER, REPO, true);

            verify(snapshotRepository).deleteByOwnerAndRepo(OWNER, REPO);
            verify(snapshotRepository, never()).existsByOwnerAndRepo(any(), any());
            assertThat(result.fromCache()).isFalse();
        }

        @Test
        void entityFields_areParsedCorrectlyFromValidRow() throws IOException {
            when(csvLoader.loadRowsForRepository(OWNER, REPO)).thenReturn(parseCsv(minimalValidCsv()));
            stubEmptyResponse();

            service.ingest(OWNER, REPO, true);

            ArgumentCaptor<List<QualityMetricSnapshotEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(snapshotRepository).saveAll(captor.capture());
            QualityMetricSnapshotEntity entity = captor.getValue().getFirst();

            assertThat(entity.getOwner()).isEqualTo(OWNER);
            assertThat(entity.getRepo()).isEqualTo(REPO);
            assertThat(entity.getPullRequestNumber()).isEqualTo(5);
            assertThat(entity.getPullRequestMerged()).isTrue();
            assertThat(entity.getBugs()).isEqualTo(3);
            assertThat(entity.getCoverage()).isEqualTo(87.5);
            assertThat(entity.getSourceRowId()).isEqualTo(100L);
            assertThat(entity.getSourceCreatedAt()).isNotNull();
            assertThat(entity.getIssuesSummary()).isInstanceOf(Map.class);
        }

        @Test
        void malformedNumericAndJsonFields_becomeNullWithoutThrowing() throws IOException {
            when(csvLoader.loadRowsForRepository(OWNER, REPO)).thenReturn(parseCsv(malformedValuesCsv()));
            stubEmptyResponse();

            service.ingest(OWNER, REPO, true);

            ArgumentCaptor<List<QualityMetricSnapshotEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(snapshotRepository).saveAll(captor.capture());
            QualityMetricSnapshotEntity entity = captor.getValue().getFirst();

            assertThat(entity.getPullRequestNumber()).isNull();
            assertThat(entity.getBugs()).isNull();
            assertThat(entity.getCoverage()).isNull();
            assertThat(entity.getSourceRowId()).isNull();
            assertThat(entity.getIssuesSummary()).isNull();
            assertThat(entity.getSourceCreatedAt()).isNull();
        }

        @Test
        void blankAndUnmappedColumns_becomeNull() throws IOException {
            when(csvLoader.loadRowsForRepository(OWNER, REPO)).thenReturn(parseCsv(sparseHeaderCsv()));
            stubEmptyResponse();

            service.ingest(OWNER, REPO, true);

            ArgumentCaptor<List<QualityMetricSnapshotEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(snapshotRepository).saveAll(captor.capture());
            QualityMetricSnapshotEntity entity = captor.getValue().getFirst();

            // pullRequestTitle column not present in header at all -> unmapped -> null
            assertThat(entity.getPullRequestTitle()).isNull();
            // baseBranchName present but blank -> null
            assertThat(entity.getBaseBranchName()).isNull();
            assertThat(entity.getAnalysisRole()).isEqualTo("pr_base");
        }
    }

    @Nested
    class GetStoredAndHasStored {
        @Test
        void getStored_buildsResponseFromRepository() {
            QualityMetricSnapshotEntity entity = QualityMetricSnapshotEntity.builder()
                    .owner(OWNER).repo(REPO).analysisRole("pr_base").build();
            when(snapshotRepository.findByOwnerAndRepoOrderByPullRequestNumberAscAnalysisRoleAsc(OWNER, REPO))
                    .thenReturn(List.of(entity));
            QualityMetricSnapshotDto dto = QualityMetricSnapshotDto.builder().analysisRole("pr_base").build();
            when(snapshotMapper.toDto(entity)).thenReturn(dto);

            QualityMetricsResponse response = service.getStored(OWNER, REPO);

            assertThat(response.getOrganization()).isEqualTo(OWNER);
            assertThat(response.getRepo()).isEqualTo(REPO);
            assertThat(response.getTotalRecords()).isEqualTo(1);
            assertThat(response.getRecords()).containsExactly(dto);
        }

        @Test
        void hasStoredMetrics_delegatesToRepository() {
            when(snapshotRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);
            assertThat(service.hasStoredMetrics(OWNER, REPO)).isTrue();
        }
    }

    @Nested
    class BulkIngest {
        @Test
        void skipsAlreadyCachedRepositories_whenNotForcing() {
            RepoKey key1 = new RepoKey("owner1", "repo1");
            RepoKey key2 = new RepoKey("owner2", "repo2");
            Map<RepoKey, List<CSVRecord>> grouped = new LinkedHashMap<>();
            grouped.put(key1, List.of());
            grouped.put(key2, List.of());
            when(csvLoader.loadAllGroupedByRepository()).thenReturn(grouped);
            when(snapshotRepository.existsByOwnerAndRepo("owner1", "repo1")).thenReturn(true);
            when(snapshotRepository.existsByOwnerAndRepo("owner2", "repo2")).thenReturn(true);

            BulkIngestSummary summary = service.bulkIngest(false);

            assertThat(summary.getTotalRepositoriesInDataset()).isEqualTo(2);
            assertThat(summary.getRepositoriesFromCache()).isEqualTo(2);
            assertThat(summary.getRepositoriesIngested()).isZero();
            verify(snapshotRepository, never()).saveAll(anyList());
        }

        @Test
        void ingestsUncachedRepositories_andAccumulatesRecordCounts() throws IOException {
            RepoKey key1 = new RepoKey("owner1", "repo1");
            Map<RepoKey, List<CSVRecord>> grouped = new LinkedHashMap<>();
            grouped.put(key1, parseCsv(minimalValidCsv()));
            when(csvLoader.loadAllGroupedByRepository()).thenReturn(grouped);
            when(snapshotRepository.existsByOwnerAndRepo("owner1", "repo1")).thenReturn(false);

            BulkIngestSummary summary = service.bulkIngest(false);

            assertThat(summary.getRepositoriesIngested()).isEqualTo(1);
            assertThat(summary.getTotalRecordsIngested()).isEqualTo(1);
            verify(snapshotRepository).saveAll(anyList());
        }

        @Test
        void forceNewAnalysis_deletesEachRepositoryBeforeReingesting() throws IOException {
            RepoKey key1 = new RepoKey("owner1", "repo1");
            Map<RepoKey, List<CSVRecord>> grouped = new LinkedHashMap<>();
            grouped.put(key1, parseCsv(minimalValidCsv()));
            when(csvLoader.loadAllGroupedByRepository()).thenReturn(grouped);

            service.bulkIngest(true);

            verify(snapshotRepository).deleteByOwnerAndRepo("owner1", "repo1");
            verify(snapshotRepository, never()).existsByOwnerAndRepo(any(), any());
        }
    }

    // --- CSV fixtures ---

    private String minimalValidCsv() {
        return """
                repositoryUrl,pullRequestNumber,pullRequestTitle,pullRequestUrl,pullRequestState,\
                pullRequestMerged,baseBranchName,headBranchName,analysisRole,commitHash,\
                firstPullRequestCommitHash,closingCommitHash,sonarProjectKey,sonarProjectName,\
                sonarDashboardUrl,sonarTaskId,sonarAnalysisId,qualityGateStatus,bugs,vulnerabilities,\
                codeSmells,securityHotspots,coverage,duplicatedLinesDensity,ncloc,complexity,\
                cognitiveComplexity,softwareQualityReliabilityIssues,softwareQualityMaintainabilityIssues,\
                softwareQualitySecurityIssues,issuesSummaryJson,Category,id,createdAt,updatedAt
                https://github.com/apache/hello-world,5,Fix bug,https://github.com/apache/hello-world/pull/5,closed,\
                true,main,fix-branch,pr_base,abc123,def456,ghi789,proj-key,Proj Name,\
                https://sonar/dashboard,task-1,analysis-1,OK,3,0,5,1,87.5,2.1,1000,50,10,1,2,0,\
                {"total":0},software_quality,100,2024-01-01 10:00:00.000 +0200,2024-01-02 10:00:00.000 +0200
                """;
    }

    private String malformedValuesCsv() {
        return """
                repositoryUrl,pullRequestNumber,analysisRole,bugs,coverage,issuesSummaryJson,id,createdAt
                https://github.com/apache/hello-world,not-a-number,pr_base,not-a-number,not-a-double,\
                not-valid-json,not-a-long,not-a-timestamp
                """;
    }

    private String sparseHeaderCsv() {
        return """
                repositoryUrl,analysisRole,baseBranchName
                https://github.com/apache/hello-world,pr_base,
                """;
    }
}
