package com.thesis.qualitygateanalyzer.service.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.EnforcementStatus;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateCategory;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.*;
import com.thesis.qualitygateanalyzer.entity.qualitygate.*;
import com.thesis.qualitygateanalyzer.mapper.QualityGateDetectionMapper;
import com.thesis.qualitygateanalyzer.repository.qualitygate.DetectedRepositoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistenceServiceImplTest {

    @Mock
    private DetectedRepositoryRepository repositoryRepository;
    @Mock
    private QualityGateDetectionMapper mapper;

    private PersistenceServiceImpl service;

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";

    @BeforeEach
    void setUp() {
        service = new PersistenceServiceImpl(repositoryRepository, mapper);
    }

    @Nested
    class HasDetection {
        @Test
        void delegatesToRepository_true() {
            when(repositoryRepository.existsByOwnerAndRepoAndIsCurrentTrue(OWNER, REPO)).thenReturn(true);
            assertThat(service.hasDetection(OWNER, REPO)).isTrue();
        }

        @Test
        void delegatesToRepository_false() {
            when(repositoryRepository.existsByOwnerAndRepoAndIsCurrentTrue(OWNER, REPO)).thenReturn(false);
            assertThat(service.hasDetection(OWNER, REPO)).isFalse();
        }
    }

    @Nested
    class LoadDetection {
        @Test
        void absent_returnsEmptyOptional() {
            when(repositoryRepository.findByOwnerAndRepoAndIsCurrentTrue(OWNER, REPO)).thenReturn(Optional.empty());
            assertThat(service.loadDetection(OWNER, REPO)).isEmpty();
        }

        @Test
        void present_mapsToDomain_withMinimalEntity() {
            RepositoryEntity entity = RepositoryEntity.builder()
                    .owner(OWNER).repo(REPO).url("https://github.com/octocat/hello-world")
                    .stars(5).forks(1).hasQualityGate(false)
                    .detectionTimeMs(10L).apiCallsMade(2)
                    .build();
            when(repositoryRepository.findByOwnerAndRepoAndIsCurrentTrue(OWNER, REPO)).thenReturn(Optional.of(entity));
            when(mapper.detectionsToDomain(any())).thenReturn(List.of());
            when(mapper.thesisRelevantDetectionsToDomain(any())).thenReturn(List.of());
            when(mapper.workflowsToDomain(any())).thenReturn(List.of());
            when(mapper.mapJsonToBranchProtection(any())).thenReturn(null);
            when(mapper.stringSetToToolSet(any())).thenReturn(java.util.Set.of());
            when(mapper.enforcementToDomain(any())).thenReturn(null);

            Optional<RepositoryDetectionResult> result = service.loadDetection(OWNER, REPO);

            assertThat(result).isPresent();
            assertThat(result.get().getOwner()).isEqualTo(OWNER);
            assertThat(result.get().getStars()).isEqualTo(5);
            assertThat(result.get().getQualityGateHistory()).isNull();
        }

        @Test
        void present_mapsHistoryDetection_whenToolIntroductionsExist() {
            RepositoryEntity entity = RepositoryEntity.builder()
                    .owner(OWNER).repo(REPO)
                    .firstCommitSha("abc123").firstCommitDate(Instant.parse("2020-01-01T00:00:00Z"))
                    .totalCommits(50)
                    .build();

            ToolIntroductionEntity introEntity = ToolIntroductionEntity.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE)
                    .build();
            entity.addToolIntroduction(introEntity);

            HistoryDetectionMetadataEntity metadataEntity = HistoryDetectionMetadataEntity.builder()
                    .success(true).toolsScanned(1).build();
            entity.setHistoryMetadata(metadataEntity);

            when(repositoryRepository.findByOwnerAndRepoAndIsCurrentTrue(OWNER, REPO)).thenReturn(Optional.of(entity));
            when(mapper.detectionsToDomain(any())).thenReturn(List.of());
            when(mapper.thesisRelevantDetectionsToDomain(any())).thenReturn(List.of());
            when(mapper.workflowsToDomain(any())).thenReturn(List.of());
            when(mapper.stringSetToToolSet(any())).thenReturn(java.util.Set.of());
            when(mapper.enforcementToDomain(any())).thenReturn(null);

            QGToolIntroduction domainIntro = QGToolIntroduction.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE)
                    .effectiveDate(Instant.parse("2021-06-01T00:00:00Z"))
                    .build();
            when(mapper.toolIntroductionToDomain(introEntity)).thenReturn(domainIntro);

            QualityGateHistoryDetection.HistoryDetectionMetadata domainMetadata =
                    QualityGateHistoryDetection.HistoryDetectionMetadata.builder().success(true).build();
            when(mapper.metadataToDomain(metadataEntity)).thenReturn(domainMetadata);

            Optional<RepositoryDetectionResult> result = service.loadDetection(OWNER, REPO);

            assertThat(result).isPresent();
            QualityGateHistoryDetection history = result.get().getQualityGateHistory();
            assertThat(history).isNotNull();
            assertThat(history.getToolIntroductions()).hasSize(1);
            assertThat(history.getEarliestIntroduction()).isEqualTo(domainIntro);
            assertThat(history.getLatestIntroduction()).isEqualTo(domainIntro);
            assertThat(history.getRepoFirstCommit().getSha()).isEqualTo("abc123");
            assertThat(history.getTotalRepoCommits()).isEqualTo(50);
            assertThat(history.getMetadata()).isEqualTo(domainMetadata);
        }

        @Test
        void present_toolIntroductionsWithNullEffectiveDate_areExcludedFromEarliestLatest() {
            RepositoryEntity entity = RepositoryEntity.builder().owner(OWNER).repo(REPO).build();
            ToolIntroductionEntity introEntity = ToolIntroductionEntity.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE).build();
            entity.addToolIntroduction(introEntity);

            when(repositoryRepository.findByOwnerAndRepoAndIsCurrentTrue(OWNER, REPO)).thenReturn(Optional.of(entity));
            when(mapper.detectionsToDomain(any())).thenReturn(List.of());
            when(mapper.thesisRelevantDetectionsToDomain(any())).thenReturn(List.of());
            when(mapper.workflowsToDomain(any())).thenReturn(List.of());
            when(mapper.stringSetToToolSet(any())).thenReturn(java.util.Set.of());
            when(mapper.enforcementToDomain(any())).thenReturn(null);

            QGToolIntroduction domainIntro = QGToolIntroduction.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE)
                    .effectiveDate(null)
                    .build();
            when(mapper.toolIntroductionToDomain(introEntity)).thenReturn(domainIntro);

            Optional<RepositoryDetectionResult> result = service.loadDetection(OWNER, REPO);

            QualityGateHistoryDetection history = result.get().getQualityGateHistory();
            assertThat(history.getEarliestIntroduction()).isNull();
            assertThat(history.getLatestIntroduction()).isNull();
        }
    }

    @Nested
    class SaveDetection {

        @Test
        void forceNew_noExistingVersion_startsAtVersionOne() {
            RepositoryDetectionResult result = minimalResult();
            when(repositoryRepository.findMaxVersionByOwnerAndRepo(OWNER, REPO)).thenReturn(null);
            RepositoryEntity mappedEntity = RepositoryEntity.builder().owner(OWNER).repo(REPO).build();
            when(mapper.repositoryResultToEntity(eq(result), eq(1))).thenReturn(mappedEntity);

            service.saveDetection(result, true);

            verify(repositoryRepository).markExistingAsNotCurrent(OWNER, REPO);
            verify(repositoryRepository, never()).findByOwnerAndRepoAndIsCurrentTrue(any(), any());
            verify(repositoryRepository).save(mappedEntity);
        }

        @Test
        void forceNew_existingVersionThree_incrementsToFour() {
            RepositoryDetectionResult result = minimalResult();
            when(repositoryRepository.findMaxVersionByOwnerAndRepo(OWNER, REPO)).thenReturn(3);
            RepositoryEntity mappedEntity = RepositoryEntity.builder().owner(OWNER).repo(REPO).build();
            when(mapper.repositoryResultToEntity(eq(result), eq(4))).thenReturn(mappedEntity);

            service.saveDetection(result, true);

            verify(mapper).repositoryResultToEntity(result, 4);
            verify(repositoryRepository).save(mappedEntity);
        }

        @Test
        void notForceNew_existingPresent_deletesBeforeSaving() {
            RepositoryDetectionResult result = minimalResult();
            RepositoryEntity existing = RepositoryEntity.builder().owner(OWNER).repo(REPO).build();
            when(repositoryRepository.findByOwnerAndRepoAndIsCurrentTrue(OWNER, REPO)).thenReturn(Optional.of(existing));
            RepositoryEntity mappedEntity = RepositoryEntity.builder().owner(OWNER).repo(REPO).build();
            when(mapper.repositoryResultToEntity(eq(result), eq(1))).thenReturn(mappedEntity);

            service.saveDetection(result, false);

            verify(repositoryRepository).delete(existing);
            verify(repositoryRepository, never()).markExistingAsNotCurrent(any(), any());
            verify(repositoryRepository).save(mappedEntity);
        }

        @Test
        void notForceNew_noExisting_doesNotDelete() {
            RepositoryDetectionResult result = minimalResult();
            when(repositoryRepository.findByOwnerAndRepoAndIsCurrentTrue(OWNER, REPO)).thenReturn(Optional.empty());
            RepositoryEntity mappedEntity = RepositoryEntity.builder().owner(OWNER).repo(REPO).build();
            when(mapper.repositoryResultToEntity(eq(result), eq(1))).thenReturn(mappedEntity);

            service.saveDetection(result, false);

            verify(repositoryRepository, never()).delete(any());
            verify(repositoryRepository).save(mappedEntity);
        }

        @Test
        void nullOptionalCollections_areSkippedGracefully() {
            RepositoryDetectionResult result = RepositoryDetectionResult.builder()
                    .owner(OWNER).repo(REPO)
                    .allDetections(null)
                    .qualityGateWorkflows(null)
                    .qualityGateHistory(null)
                    .enforcement(null)
                    .branchProtection(null)
                    .build();
            when(repositoryRepository.findByOwnerAndRepoAndIsCurrentTrue(OWNER, REPO)).thenReturn(Optional.empty());
            RepositoryEntity mappedEntity = RepositoryEntity.builder().owner(OWNER).repo(REPO).build();
            when(mapper.repositoryResultToEntity(eq(result), eq(1))).thenReturn(mappedEntity);

            service.saveDetection(result, false);

            assertThat(mappedEntity.getDetections()).isEmpty();
            assertThat(mappedEntity.getWorkflows()).isEmpty();
            assertThat(mappedEntity.getToolIntroductions()).isEmpty();
            assertThat(mappedEntity.getEnforcement()).isNull();
            assertThat(mappedEntity.getHistoryMetadata()).isNull();
            assertThat(mappedEntity.getFirstCommitSha()).isNull();
            verify(repositoryRepository).save(mappedEntity);
        }

        @Test
        void fullResult_wiresAllNestedCollectionsOntoEntity() {
            QualityGateDetection detection = QualityGateDetection.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE)
                    .sourceFile("a").sourceType(QualityGateDetection.SourceType.CONFIG_FILE)
                    .evidenceFound(List.of()).confidenceScore(0.5).build();

            QualityGateWorkflow workflow = QualityGateWorkflow.builder()
                    .workflowFile("ci.yml").workflowName("CI").tools(List.of(QualityGateTool.PMD))
                    .triggersOnPR(true).buildCommands(List.of()).build();

            QGFileIntroduction configIntro = QGFileIntroduction.builder().filePath("pom.xml").build();
            QGFileIntroduction ciIntro = QGFileIntroduction.builder().filePath("ci.yml").build();
            QGToolIntroduction toolIntro = QGToolIntroduction.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE)
                    .configIntroductions(List.of(configIntro))
                    .ciIntroductions(List.of(ciIntro))
                    .build();

            QualityGateHistoryDetection.HistoryDetectionMetadata metadata =
                    QualityGateHistoryDetection.HistoryDetectionMetadata.builder().success(true).build();
            QualityGateHistoryDetection history = QualityGateHistoryDetection.builder()
                    .repoFirstCommit(CommitInfo.builder().sha("sha1").date(Instant.parse("2020-01-01T00:00:00Z")).build())
                    .totalRepoCommits(99)
                    .toolIntroductions(List.of(toolIntro))
                    .metadata(metadata)
                    .build();

            CheckRun checkRun = CheckRun.builder().id(1L).name("PMD").build();
            WorkflowRun workflowRun = WorkflowRun.builder().id(2L).build();
            PRDetectionResult prSample = PRDetectionResult.builder()
                    .prNumber(7).workflowRuns(List.of(workflowRun)).qualityGateCheckRuns(List.of(checkRun)).build();

            EnforcementDetectionResult.ToolStats toolStats = EnforcementDetectionResult.ToolStats.builder()
                    .tool(QualityGateTool.PMD).failures(1).enforced(1).bypassed(0).isRequiredCheck(true).build();
            EnforcementDetectionResult enforcement = EnforcementDetectionResult.builder()
                    .status(EnforcementStatus.STRICTLY_ENFORCED)
                    .byTool(Map.of(QualityGateTool.PMD, toolStats))
                    .samplePRs(List.of(prSample))
                    .branchProtection(null)
                    .fallbackInfo(null)
                    .build();

            RepositoryDetectionResult result = RepositoryDetectionResult.builder()
                    .owner(OWNER).repo(REPO)
                    .allDetections(List.of(detection))
                    .qualityGateWorkflows(List.of(workflow))
                    .qualityGateHistory(history)
                    .enforcement(enforcement)
                    .build();

            when(repositoryRepository.findByOwnerAndRepoAndIsCurrentTrue(OWNER, REPO)).thenReturn(Optional.empty());
            RepositoryEntity mappedEntity = RepositoryEntity.builder().owner(OWNER).repo(REPO).build();
            when(mapper.repositoryResultToEntity(eq(result), eq(1))).thenReturn(mappedEntity);

            QGDetectionEntity detectionEntity = QGDetectionEntity.builder().tool(QualityGateTool.PMD).build();
            when(mapper.detectionToEntity(detection)).thenReturn(detectionEntity);

            QGWorkflowEntity workflowEntity = QGWorkflowEntity.builder().workflowFile("ci.yml").build();
            when(mapper.workflowToEntity(workflow)).thenReturn(workflowEntity);

            ToolIntroductionEntity toolIntroEntity = ToolIntroductionEntity.builder().tool(QualityGateTool.PMD).build();
            when(mapper.toolIntroductionToEntity(toolIntro)).thenReturn(toolIntroEntity);

            FileIntroductionEntity configIntroEntity = FileIntroductionEntity.builder().filePath("pom.xml").build();
            when(mapper.fileIntroductionToEntity(configIntro, "CONFIG")).thenReturn(configIntroEntity);
            FileIntroductionEntity ciIntroEntity = FileIntroductionEntity.builder().filePath("ci.yml").build();
            when(mapper.fileIntroductionToEntity(ciIntro, "CI")).thenReturn(ciIntroEntity);

            HistoryDetectionMetadataEntity metadataEntity = HistoryDetectionMetadataEntity.builder().success(true).build();
            when(mapper.metadataToEntity(metadata)).thenReturn(metadataEntity);

            EnforcementEntity enforcementEntity = EnforcementEntity.builder().status(EnforcementStatus.STRICTLY_ENFORCED).build();
            when(mapper.enforcementToEntity(enforcement)).thenReturn(enforcementEntity);

            EnforcementByToolEntity toolStatsEntity = EnforcementByToolEntity.builder().tool(QualityGateTool.PMD).build();
            when(mapper.toolStatsToEntity(toolStats)).thenReturn(toolStatsEntity);

            PRSampleEntity prSampleEntity = PRSampleEntity.builder().prNumber(7).build();
            when(mapper.prSampleToEntity(prSample)).thenReturn(prSampleEntity);

            PRWorkflowRunEntity workflowRunEntity = PRWorkflowRunEntity.builder().runId(2L).build();
            when(mapper.workflowRunToEntity(workflowRun)).thenReturn(workflowRunEntity);

            PRCheckRunEntity checkRunEntity = PRCheckRunEntity.builder().checkRunId(1L).build();
            when(mapper.checkRunToEntity(checkRun)).thenReturn(checkRunEntity);

            service.saveDetection(result, false);

            assertThat(mappedEntity.getDetections()).containsExactly(detectionEntity);
            assertThat(mappedEntity.getWorkflows()).containsExactly(workflowEntity);
            assertThat(mappedEntity.getFirstCommitSha()).isEqualTo("sha1");
            assertThat(mappedEntity.getTotalCommits()).isEqualTo(99);
            assertThat(mappedEntity.getToolIntroductions()).containsExactly(toolIntroEntity);
            assertThat(toolIntroEntity.getFileIntroductions()).containsExactlyInAnyOrder(configIntroEntity, ciIntroEntity);
            assertThat(mappedEntity.getHistoryMetadata()).isEqualTo(metadataEntity);
            assertThat(mappedEntity.getEnforcement()).isEqualTo(enforcementEntity);
            assertThat(enforcementEntity.getByTool()).containsExactly(toolStatsEntity);
            assertThat(enforcementEntity.getSamplePRs()).containsExactly(prSampleEntity);
            assertThat(prSampleEntity.getWorkflowRuns()).containsExactly(workflowRunEntity);
            assertThat(prSampleEntity.getCheckRuns()).containsExactly(checkRunEntity);
        }

        private RepositoryDetectionResult minimalResult() {
            return RepositoryDetectionResult.builder().owner(OWNER).repo(REPO).build();
        }
    }

    @Nested
    class DeleteDetection {
        @Test
        void delegatesToRepository() {
            service.deleteDetection(OWNER, REPO);
            verify(repositoryRepository).deleteByOwnerAndRepo(OWNER, REPO);
        }
    }

    @Nested
    class ListOperations {
        @Test
        void listAllDetections_mapsEachEntity() {
            RepositoryEntity e1 = RepositoryEntity.builder().owner(OWNER).repo(REPO).build();
            RepositoryEntity e2 = RepositoryEntity.builder().owner("other").repo("repo2").build();
            when(repositoryRepository.findByIsCurrentTrueOrderByDetectedAtDesc()).thenReturn(List.of(e1, e2));
            when(mapper.detectionsToDomain(any())).thenReturn(List.of());
            when(mapper.thesisRelevantDetectionsToDomain(any())).thenReturn(List.of());
            when(mapper.workflowsToDomain(any())).thenReturn(List.of());
            when(mapper.stringSetToToolSet(any())).thenReturn(java.util.Set.of());
            when(mapper.enforcementToDomain(any())).thenReturn(null);

            List<RepositoryDetectionResult> results = service.listAllDetections();

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getOwner()).isEqualTo(OWNER);
            assertThat(results.get(1).getOwner()).isEqualTo("other");
        }

        @Test
        void listWithQualityGate_mapsEachEntity() {
            RepositoryEntity e1 = RepositoryEntity.builder().owner(OWNER).repo(REPO).hasQualityGate(true).build();
            when(repositoryRepository.findByIsCurrentTrueAndHasQualityGateTrueOrderByDetectedAtDesc())
                    .thenReturn(List.of(e1));
            when(mapper.detectionsToDomain(any())).thenReturn(List.of());
            when(mapper.thesisRelevantDetectionsToDomain(any())).thenReturn(List.of());
            when(mapper.workflowsToDomain(any())).thenReturn(List.of());
            when(mapper.stringSetToToolSet(any())).thenReturn(java.util.Set.of());
            when(mapper.enforcementToDomain(any())).thenReturn(null);

            List<RepositoryDetectionResult> results = service.listWithQualityGate();

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().isHasQualityGate()).isTrue();
        }
    }
}
