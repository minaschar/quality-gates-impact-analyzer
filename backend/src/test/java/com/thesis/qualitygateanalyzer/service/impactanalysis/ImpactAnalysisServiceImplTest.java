package com.thesis.qualitygateanalyzer.service.impactanalysis;

import com.thesis.qualitygateanalyzer.domain.enums.ImpactTrend;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateCategory;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CommitInfo;
import com.thesis.qualitygateanalyzer.domain.qualitygate.QGToolIntroduction;
import com.thesis.qualitygateanalyzer.domain.qualitygate.QualityGateHistoryDetection;
import com.thesis.qualitygateanalyzer.domain.qualitygate.RepositoryDetectionResult;
import com.thesis.qualitygateanalyzer.dto.response.*;
import com.thesis.qualitygateanalyzer.entity.commit.CommitHistoryEntity;
import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactAnalysisEntity;
import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactComparisonEntity;
import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactMetricsSnapshotEntity;
import com.thesis.qualitygateanalyzer.exception.ImpactAnalysisNotFoundException;
import com.thesis.qualitygateanalyzer.mapper.ImpactAnalysisMapper;
import com.thesis.qualitygateanalyzer.repository.commit.CommitHistoryRepository;
import com.thesis.qualitygateanalyzer.repository.impactanalysis.ImpactAnalysisRepository;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import com.thesis.qualitygateanalyzer.service.qualitygate.PersistenceService;
import com.thesis.qualitygateanalyzer.service.qualitygate.QualityGateDetectionService;
import com.thesis.qualitygateanalyzer.service.qualitymetrics.QualityMetricsCsvLoader;
import com.thesis.qualitygateanalyzer.service.qualitymetrics.QualityMetricsIngestionService;
import com.thesis.qualitygateanalyzer.service.qualitymetrics.QualityMetricsIngestionService.IngestResult;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;

@ExtendWith(MockitoExtension.class)
class ImpactAnalysisServiceImplTest {

    @Mock
    private QualityGateDetectionService detectionService;
    @Mock
    private PersistenceService persistenceService;
    @Mock
    private QualityMetricsIngestionService metricsIngestionService;
    @Mock
    private QualityMetricsCsvLoader metricsCsvLoader;
    @Mock
    private CommitHistoryRepository commitHistoryRepository;
    @Mock
    private GitHubApiClient githubClient;
    @Mock
    private ImpactAnalysisRepository impactAnalysisRepository;
    @Mock
    private ImpactAnalysisMapper mapper;

    private ImpactAnalysisServiceImpl service;

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";
    private static final String URL = "https://github.com/octocat/hello-world";

    @BeforeEach
    void setUp() {
        service = new ImpactAnalysisServiceImpl(detectionService, persistenceService, metricsIngestionService,
                metricsCsvLoader, commitHistoryRepository, githubClient, impactAnalysisRepository, mapper);
        lenient().when(mapper.toComparisonDtos(any())).thenReturn(List.of());
        lenient().when(mapper.toTimelineDtos(any())).thenReturn(List.of());
        // Default: metrics already ingested, so loadMetricRows()'s CSV-loader pre-check is
        // skipped and it goes straight to ingest(); tests needing the "nothing ingested yet"
        // path override this explicitly.
        lenient().when(metricsIngestionService.hasStoredMetrics(OWNER, REPO)).thenReturn(true);
    }

    private RepositoryDetectionResult detectionWithoutQualityGate() {
        return RepositoryDetectionResult.builder().owner(OWNER).repo(REPO).url(URL).hasQualityGate(false).build();
    }

    private QualityMetricSnapshotDto snapshot(int prNumber, String role, String sha, Integer bugs, Double coverage) {
        return QualityMetricSnapshotDto.builder()
                .pullRequestNumber(prNumber)
                .analysisRole(role)
                .commitHash(sha)
                .bugs(bugs)
                .vulnerabilities(0)
                .codeSmells(bugs == null ? null : bugs * 2)
                .securityHotspots(0)
                .coverage(coverage)
                .duplicatedLinesDensity(1.0)
                .ncloc(1000)
                .complexity(50)
                .cognitiveComplexity(20)
                .softwareQualityReliabilityIssues(0)
                .softwareQualityMaintainabilityIssues(0)
                .softwareQualitySecurityIssues(0)
                .build();
    }

    @Nested
    class Analyze {

        @Test
        void cachedResult_skipsRecomputation() {
            ImpactAnalysisEntity cached = ImpactAnalysisEntity.builder()
                    .owner(OWNER).repo(REPO).overallTrend(ImpactTrend.IMPROVED).build();
            when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.of(cached));

            ImpactAnalysisResponse result = service.analyze(OWNER, REPO, false);

            assertThat(result.isCached()).isTrue();
            assertThat(result.getOverallTrend()).isEqualTo(ImpactTrend.IMPROVED);
            verifyNoInteractions(detectionService, metricsIngestionService, githubClient);
            verify(impactAnalysisRepository, never()).save(any());
        }

        @Test
        void forceNewAnalysis_skipsCacheLookup_andReusesExistingDetection() {
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(detectionWithoutQualityGate()));

            service.analyze(OWNER, REPO, true);

            verify(impactAnalysisRepository, never()).findByOwnerAndRepo(any(), any());
            verifyNoInteractions(detectionService); // already detected -> reused, never re-run
        }

        @Test
        void noQualityGate_returnsEarly_withoutPersisting() {
            when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.empty());
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(detectionWithoutQualityGate()));

            ImpactAnalysisResponse result = service.analyze(OWNER, REPO, false);

            assertThat(result.isHasQualityGate()).isFalse();
            assertThat(result.getComparisons()).isEmpty();
            assertThat(result.getTimeline()).isEmpty();
            assertThat(result.getOverallTrend()).isEqualTo(ImpactTrend.INSUFFICIENT_DATA);
            verifyNoInteractions(metricsIngestionService);
            verify(impactAnalysisRepository, never()).save(any());
            verify(detectionService, never()).detect(anyString());
        }

        @Test
        void neverDetected_runsDetectionAutomatically() {
            when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.empty());
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.empty());
            when(detectionService.detect(URL)).thenReturn(detectionWithoutQualityGate());

            service.analyze(OWNER, REPO, false);

            verify(detectionService).detect(URL);
            verify(persistenceService).saveDetection(any(), eq(false));
        }

        @Test
        void noDatedIntroductions_persistsInsufficientData_withoutLoadingMetrics() {
            QGToolIntroduction undated = QGToolIntroduction.builder()
                    .tool(QualityGateTool.CHECKSTYLE).category(QualityGateCategory.CODE_STYLE)
                    .effectiveDate(null)
                    .build();
            RepositoryDetectionResult detection = RepositoryDetectionResult.builder()
                    .owner(OWNER).repo(REPO).url(URL).hasQualityGate(true)
                    .qualityGateHistory(QualityGateHistoryDetection.builder()
                            .toolIntroductions(List.of(undated)).build())
                    .build();
            when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.empty());
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(detection));

            ImpactAnalysisResponse result = service.analyze(OWNER, REPO, false);

            assertThat(result.isHasQualityGate()).isTrue();
            assertThat(result.getOverallTrend()).isEqualTo(ImpactTrend.INSUFFICIENT_DATA);
            assertThat(result.getToolsAnalyzed()).isZero();
            verifyNoInteractions(metricsIngestionService);
            verify(impactAnalysisRepository).save(any());
        }

        @Test
        void noMetricsInDataset_persistsInsufficientData_withoutCallingIngest() {
            // Regression test: ingest() must never be called (let alone throw) when we already
            // know via the CSV loader that there's nothing to ingest, because ingest() is
            // @Transactional and joins analyze()'s transaction -- if it threw and we caught it,
            // the shared transaction would be marked rollback-only and analyze() returning
            // normally afterward would fail at commit with UnexpectedRollbackException.
            RepositoryDetectionResult detection = detectionWithDatedIntroduction();
            when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.empty());
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(detection));
            when(metricsIngestionService.hasStoredMetrics(OWNER, REPO)).thenReturn(false);
            when(metricsCsvLoader.loadRowsForRepository(OWNER, REPO)).thenReturn(List.of());

            ImpactAnalysisResponse result = service.analyze(OWNER, REPO, false);

            assertThat(result.getOverallTrend()).isEqualTo(ImpactTrend.INSUFFICIENT_DATA);
            verify(impactAnalysisRepository).save(any());
            verify(metricsIngestionService, never()).ingest(any(), any(), anyBoolean());
        }

        private RepositoryDetectionResult detectionWithDatedIntroduction() {
            QGToolIntroduction introduction = QGToolIntroduction.builder()
                    .tool(QualityGateTool.CHECKSTYLE).category(QualityGateCategory.CODE_STYLE)
                    .effectiveDate(Instant.parse("2024-06-01T00:00:00Z"))
                    .effectiveIntroductionCommit(CommitInfo.builder().sha("intro-sha").build())
                    .build();
            return RepositoryDetectionResult.builder()
                    .owner(OWNER).repo(REPO).url(URL).hasQualityGate(true)
                    .qualityGateHistory(QualityGateHistoryDetection.builder()
                            .toolIntroductions(List.of(introduction)).build())
                    .build();
        }

        @Nested
        class HappyPath {

            @Test
            void computesBeforeAfterComparison_viaCommitHistoryDates() {
                RepositoryDetectionResult detection = detectionWithDatedIntroduction();
                when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.empty());
                when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(detection));

                QualityMetricSnapshotDto before = snapshot(1, "pr_base", "sha-before", 10, 50.0);
                QualityMetricSnapshotDto after = snapshot(2, "pr_closed", "sha-after", 2, 90.0);
                when(metricsIngestionService.ingest(OWNER, REPO, false)).thenReturn(
                        new IngestResult(QualityMetricsResponse.builder().records(List.of(before, after)).build(), false));

                when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);
                when(commitHistoryRepository.findByOwnerAndRepoOrderByCommitDateAsc(OWNER, REPO)).thenReturn(List.of(
                        CommitHistoryEntity.builder().owner(OWNER).repo(REPO)
                                .commitSha("sha-before").commitDate(Instant.parse("2024-01-01T00:00:00Z")).build(),
                        CommitHistoryEntity.builder().owner(OWNER).repo(REPO)
                                .commitSha("sha-after").commitDate(Instant.parse("2024-12-01T00:00:00Z")).build()
                ));

                service.analyze(OWNER, REPO, false);

                ArgumentCaptor<ImpactAnalysisEntity> captor = ArgumentCaptor.forClass(ImpactAnalysisEntity.class);
                verify(impactAnalysisRepository).save(captor.capture());
                ImpactAnalysisEntity saved = captor.getValue();

                assertThat(saved.getToolsAnalyzedCount()).isEqualTo(1);
                assertThat(saved.getTotalSnapshotsBefore()).isEqualTo(1);
                assertThat(saved.getTotalSnapshotsAfter()).isEqualTo(1);
                assertThat(saved.getOverallTrend()).isEqualTo(ImpactTrend.IMPROVED);
                assertThat(saved.getOverallImprovementScore()).isPositive();

                assertThat(saved.getSnapshots()).hasSize(2);
                assertThat(saved.getSnapshots()).anySatisfy(s ->
                        assertThat(s.getClassification()).isEqualTo(ImpactMetricsSnapshotEntity.Classification.BEFORE));
                assertThat(saved.getSnapshots()).anySatisfy(s ->
                        assertThat(s.getClassification()).isEqualTo(ImpactMetricsSnapshotEntity.Classification.AFTER));
                assertThat(saved.getSnapshots()).allSatisfy(s ->
                        assertThat(s.getDateSource()).isEqualTo(ImpactMetricsSnapshotEntity.DateSource.COMMIT_HISTORY));

                assertThat(saved.getComparisons()).hasSize(1);
                ImpactComparisonEntity comparison = saved.getComparisons().getFirst();
                assertThat(comparison.getTool()).isEqualTo(QualityGateTool.CHECKSTYLE);
                assertThat(comparison.getTrend()).isEqualTo(ImpactTrend.IMPROVED);
                assertThat(comparison.getSamplesBefore()).isEqualTo(1);
                assertThat(comparison.getSamplesAfter()).isEqualTo(1);

                @SuppressWarnings("unchecked")
                Map<String, MetricComparisonDto> metrics = (Map<String, MetricComparisonDto>) comparison.getMetricsComparison();
                MetricComparisonDto bugs = metrics.get("bugs");
                assertThat(bugs.getAvgBefore()).isEqualTo(10.0);
                assertThat(bugs.getAvgAfter()).isEqualTo(2.0);
                assertThat(bugs.getTrend()).isEqualTo(ImpactTrend.IMPROVED); // fewer bugs = improvement

                MetricComparisonDto coverage = metrics.get("coverage");
                assertThat(coverage.getAvgBefore()).isEqualTo(50.0);
                assertThat(coverage.getAvgAfter()).isEqualTo(90.0);
                assertThat(coverage.getTrend()).isEqualTo(ImpactTrend.IMPROVED); // more coverage = improvement

                MetricComparisonDto ncloc = metrics.get("ncloc");
                assertThat(ncloc.getTrend()).isNull(); // contextual only, never scored
            }

            @Test
            void degradingMetrics_produceDegradedTrend() {
                RepositoryDetectionResult detection = detectionWithDatedIntroduction();
                when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.empty());
                when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(detection));

                QualityMetricSnapshotDto before = snapshot(1, "pr_base", "sha-before", 2, 90.0);
                QualityMetricSnapshotDto after = snapshot(2, "pr_closed", "sha-after", 10, 50.0);
                when(metricsIngestionService.ingest(OWNER, REPO, false)).thenReturn(
                        new IngestResult(QualityMetricsResponse.builder().records(List.of(before, after)).build(), false));
                when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);
                when(commitHistoryRepository.findByOwnerAndRepoOrderByCommitDateAsc(OWNER, REPO)).thenReturn(List.of(
                        CommitHistoryEntity.builder().owner(OWNER).repo(REPO)
                                .commitSha("sha-before").commitDate(Instant.parse("2024-01-01T00:00:00Z")).build(),
                        CommitHistoryEntity.builder().owner(OWNER).repo(REPO)
                                .commitSha("sha-after").commitDate(Instant.parse("2024-12-01T00:00:00Z")).build()
                ));

                service.analyze(OWNER, REPO, false);

                ArgumentCaptor<ImpactAnalysisEntity> captor = ArgumentCaptor.forClass(ImpactAnalysisEntity.class);
                verify(impactAnalysisRepository).save(captor.capture());
                assertThat(captor.getValue().getOverallTrend()).isEqualTo(ImpactTrend.DEGRADED);
            }

            @Test
            void unresolvedCommit_fallsBackToPullRequestMetadata() {
                RepositoryDetectionResult detection = detectionWithDatedIntroduction();
                when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.empty());
                when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(detection));

                QualityMetricSnapshotDto row = snapshot(5, "pr_base", "unresolved-sha", 3, 70.0);
                when(metricsIngestionService.ingest(OWNER, REPO, false)).thenReturn(
                        new IngestResult(QualityMetricsResponse.builder().records(List.of(row)).build(), false));

                when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);
                when(commitHistoryRepository.findByOwnerAndRepoOrderByCommitDateAsc(OWNER, REPO)).thenReturn(List.of());
                when(githubClient.getPullRequest(OWNER, REPO, 5)).thenReturn(Optional.of(Map.of(
                        "created_at", "2024-01-01T00:00:00Z",
                        "merged_at", "2024-01-02T00:00:00Z"
                )));

                service.analyze(OWNER, REPO, false);

                ArgumentCaptor<ImpactAnalysisEntity> captor = ArgumentCaptor.forClass(ImpactAnalysisEntity.class);
                verify(impactAnalysisRepository).save(captor.capture());
                assertThat(captor.getValue().getSnapshots()).hasSize(1);
                ImpactMetricsSnapshotEntity snapshot = captor.getValue().getSnapshots().getFirst();
                assertThat(snapshot.getDateSource()).isEqualTo(ImpactMetricsSnapshotEntity.DateSource.PR_METADATA);
                assertThat(snapshot.getCommitDate()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z")); // pr_base -> created_at
            }

            @Test
            void forceNewAnalysis_withExistingRecord_deletesBeforeRecomputing() {
                RepositoryDetectionResult detection = detectionWithDatedIntroduction();
                when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(detection));

                QualityMetricSnapshotDto before = snapshot(1, "pr_base", "sha-before", 10, 50.0);
                QualityMetricSnapshotDto after = snapshot(2, "pr_closed", "sha-after", 2, 90.0);
                // forceNewAnalysis=true below must also force a fresh CSV ingestion, not just
                // recompute the impact-analysis record from stale stored metrics -- so, unlike
                // most other tests here, the CSV-loader pre-check is NOT skipped by the
                // hasStoredMetrics=true default and needs its own non-empty stub.
                when(metricsCsvLoader.loadRowsForRepository(OWNER, REPO)).thenReturn(List.of(mock(CSVRecord.class)));
                when(metricsIngestionService.ingest(OWNER, REPO, true)).thenReturn(
                        new IngestResult(QualityMetricsResponse.builder().records(List.of(before, after)).build(), false));
                when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);
                when(commitHistoryRepository.findByOwnerAndRepoOrderByCommitDateAsc(OWNER, REPO)).thenReturn(List.of(
                        CommitHistoryEntity.builder().owner(OWNER).repo(REPO)
                                .commitSha("sha-before").commitDate(Instant.parse("2024-01-01T00:00:00Z")).build(),
                        CommitHistoryEntity.builder().owner(OWNER).repo(REPO)
                                .commitSha("sha-after").commitDate(Instant.parse("2024-12-01T00:00:00Z")).build()
                ));
                when(impactAnalysisRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);

                service.analyze(OWNER, REPO, true);

                verify(impactAnalysisRepository, never()).findByOwnerAndRepo(any(), any());
                verify(impactAnalysisRepository).deleteByOwnerAndRepo(OWNER, REPO);
                verify(impactAnalysisRepository).save(any());
            }

            @Test
            void noCachedCommitHistory_fetchesFromGitHubAndCachesIt() {
                RepositoryDetectionResult detection = detectionWithDatedIntroduction();
                when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.empty());
                when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(detection));

                QualityMetricSnapshotDto row = snapshot(1, "pr_base", "abc123", 3, 70.0);
                when(metricsIngestionService.ingest(OWNER, REPO, false)).thenReturn(
                        new IngestResult(QualityMetricsResponse.builder().records(List.of(row)).build(), false));

                when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(false);
                when(githubClient.getAllCommits(OWNER, REPO)).thenReturn(new java.util.ArrayList<>(List.of(
                        Map.of("sha", "abc123", "commit", Map.of("committer", Map.of("date", "2024-01-01T00:00:00Z")))
                )));

                service.analyze(OWNER, REPO, false);

                verify(commitHistoryRepository).saveAll(anyList());
                verify(githubClient, never()).getPullRequest(any(), any(), anyInt());
            }

            @Test
            void placeholderRowsWithZeroNcloc_excludedFromComparisonMath() {
                // Regression test: a row with ncloc == 0 means SonarQube scanned zero lines of
                // code for that PR (an empty/placeholder analysis) -- every other metric on it
                // is a spurious zero, not a real measurement. Left unfiltered, an AFTER bucket
                // made entirely of such rows produces a misleadingly perfect "100% improved"
                // score (observed for real on apache/cordova-browser and apache/datasketches-hive).
                RepositoryDetectionResult detection = detectionWithDatedIntroduction();
                when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.empty());
                when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(detection));

                QualityMetricSnapshotDto before = snapshot(1, "pr_base", "sha-before", 10, 50.0);
                QualityMetricSnapshotDto placeholderAfter = QualityMetricSnapshotDto.builder()
                        .pullRequestNumber(2).analysisRole("pr_closed").commitHash("sha-after")
                        .bugs(0).vulnerabilities(0).codeSmells(0).securityHotspots(0)
                        .coverage(null).duplicatedLinesDensity(null).ncloc(0)
                        .complexity(0).cognitiveComplexity(0)
                        .softwareQualityReliabilityIssues(0).softwareQualityMaintainabilityIssues(0)
                        .softwareQualitySecurityIssues(0)
                        .build();
                when(metricsIngestionService.ingest(OWNER, REPO, false)).thenReturn(
                        new IngestResult(QualityMetricsResponse.builder().records(List.of(before, placeholderAfter)).build(), false));
                when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);
                when(commitHistoryRepository.findByOwnerAndRepoOrderByCommitDateAsc(OWNER, REPO)).thenReturn(List.of(
                        CommitHistoryEntity.builder().owner(OWNER).repo(REPO)
                                .commitSha("sha-before").commitDate(Instant.parse("2024-01-01T00:00:00Z")).build(),
                        CommitHistoryEntity.builder().owner(OWNER).repo(REPO)
                                .commitSha("sha-after").commitDate(Instant.parse("2024-12-01T00:00:00Z")).build()
                ));

                service.analyze(OWNER, REPO, false);

                ArgumentCaptor<ImpactAnalysisEntity> captor = ArgumentCaptor.forClass(ImpactAnalysisEntity.class);
                verify(impactAnalysisRepository).save(captor.capture());
                ImpactAnalysisEntity saved = captor.getValue();

                // Both rows are still persisted in the raw timeline for transparency...
                assertThat(saved.getSnapshots()).hasSize(2);
                assertThat(saved.getTotalSnapshotsBefore()).isEqualTo(1);
                assertThat(saved.getTotalSnapshotsAfter()).isEqualTo(1);

                // ...but the placeholder AFTER row is excluded from the comparison, leaving no
                // real AFTER sample -- INSUFFICIENT_DATA, not a bogus 100% improvement.
                ImpactComparisonEntity comparison = saved.getComparisons().getFirst();
                assertThat(comparison.getSamplesBefore()).isEqualTo(1);
                assertThat(comparison.getSamplesAfter()).isZero();
                assertThat(comparison.getTrend()).isEqualTo(ImpactTrend.INSUFFICIENT_DATA);
                assertThat(comparison.getImprovementScore()).isNull();
            }

            @Test
            void softwareQualityDuplicateMetrics_haveOwnTrend_butDoNotDoubleCountInScore() {
                // bugs/codeSmells and their softwareQuality* counterparts move identically here
                // (as they do in the real dataset -- SonarQube's newer taxonomy re-labels the
                // same issues). If they were both counted in the score average, the composite
                // would overweight this one signal relative to coverage/complexity/etc.
                RepositoryDetectionResult detection = detectionWithDatedIntroduction();
                when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.empty());
                when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(detection));

                QualityMetricSnapshotDto before = QualityMetricSnapshotDto.builder()
                        .pullRequestNumber(1).analysisRole("pr_base").commitHash("sha-before")
                        .bugs(10).vulnerabilities(0).codeSmells(20).securityHotspots(0)
                        .coverage(50.0).duplicatedLinesDensity(1.0).ncloc(1000)
                        .complexity(50).cognitiveComplexity(20)
                        .softwareQualityReliabilityIssues(10).softwareQualityMaintainabilityIssues(20)
                        .softwareQualitySecurityIssues(0)
                        .build();
                QualityMetricSnapshotDto after = QualityMetricSnapshotDto.builder()
                        .pullRequestNumber(2).analysisRole("pr_closed").commitHash("sha-after")
                        .bugs(0).vulnerabilities(0).codeSmells(0).securityHotspots(0)
                        .coverage(50.0).duplicatedLinesDensity(1.0).ncloc(1000)
                        .complexity(50).cognitiveComplexity(20)
                        .softwareQualityReliabilityIssues(0).softwareQualityMaintainabilityIssues(0)
                        .softwareQualitySecurityIssues(0)
                        .build();
                when(metricsIngestionService.ingest(OWNER, REPO, false)).thenReturn(
                        new IngestResult(QualityMetricsResponse.builder().records(List.of(before, after)).build(), false));
                when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);
                when(commitHistoryRepository.findByOwnerAndRepoOrderByCommitDateAsc(OWNER, REPO)).thenReturn(List.of(
                        CommitHistoryEntity.builder().owner(OWNER).repo(REPO)
                                .commitSha("sha-before").commitDate(Instant.parse("2024-01-01T00:00:00Z")).build(),
                        CommitHistoryEntity.builder().owner(OWNER).repo(REPO)
                                .commitSha("sha-after").commitDate(Instant.parse("2024-12-01T00:00:00Z")).build()
                ));

                service.analyze(OWNER, REPO, false);

                ArgumentCaptor<ImpactAnalysisEntity> captor = ArgumentCaptor.forClass(ImpactAnalysisEntity.class);
                verify(impactAnalysisRepository).save(captor.capture());
                ImpactComparisonEntity comparison = captor.getValue().getComparisons().getFirst();

                @SuppressWarnings("unchecked")
                Map<String, MetricComparisonDto> metrics = (Map<String, MetricComparisonDto>) comparison.getMetricsComparison();
                // Still reported with their own trend for display...
                assertThat(metrics.get("softwareQualityReliabilityIssues").getTrend()).isEqualTo(ImpactTrend.IMPROVED);
                assertThat(metrics.get("softwareQualityMaintainabilityIssues").getTrend()).isEqualTo(ImpactTrend.IMPROVED);

                // ...but excluded from the score: only bugs/codeSmells (each +100 normalized) plus
                // coverage/duplicatedLinesDensity/complexity/cognitiveComplexity (unchanged, 0)
                // contribute -> (100+100+0+0+0+0)/6 = 33.33, not (100+100+100+100+0+0+0+0)/8 = 50
                // if the duplicate softwareQuality* metrics were also counted.
                assertThat(comparison.getImprovementScore()).isCloseTo(200.0 / 6, org.assertj.core.data.Offset.offset(0.01));
            }
        }
    }

    @Nested
    class GetAnalysis {

        @Test
        void present_returnsMappedResponse() {
            ImpactAnalysisEntity entity = ImpactAnalysisEntity.builder()
                    .owner(OWNER).repo(REPO).overallTrend(ImpactTrend.UNCHANGED).build();
            when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.of(entity));

            ImpactAnalysisResponse result = service.getAnalysis(OWNER, REPO);

            assertThat(result.getOverallTrend()).isEqualTo(ImpactTrend.UNCHANGED);
            assertThat(result.isCached()).isTrue();
        }

        @Test
        void absent_throwsImpactAnalysisNotFoundException() {
            when(impactAnalysisRepository.findByOwnerAndRepo(OWNER, REPO)).thenReturn(Optional.empty());
            assertThrows(ImpactAnalysisNotFoundException.class, () -> service.getAnalysis(OWNER, REPO));
        }
    }

    @Nested
    class ListAll {

        @Test
        void mapsEachEntityToSummary() {
            ImpactAnalysisEntity entity = ImpactAnalysisEntity.builder()
                    .owner(OWNER).repo(REPO).toolsAnalyzedCount(2)
                    .overallTrend(ImpactTrend.IMPROVED).overallImprovementScore(12.5)
                    .build();
            when(impactAnalysisRepository.findByOrderByComputedAtDesc()).thenReturn(List.of(entity));

            List<ImpactAnalysisSummaryDto> result = service.listAll();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getOwner()).isEqualTo(OWNER);
            assertThat(result.getFirst().getToolsAnalyzed()).isEqualTo(2);
            assertThat(result.getFirst().getOverallTrend()).isEqualTo(ImpactTrend.IMPROVED);
        }

        @Test
        void empty_returnsEmptyList() {
            when(impactAnalysisRepository.findByOrderByComputedAtDesc()).thenReturn(List.of());
            assertThat(service.listAll()).isEmpty();
        }
    }
}
