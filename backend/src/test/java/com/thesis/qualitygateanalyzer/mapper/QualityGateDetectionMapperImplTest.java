package com.thesis.qualitygateanalyzer.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.qualitygateanalyzer.domain.enums.EnforcementStatus;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateCategory;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.*;
import com.thesis.qualitygateanalyzer.entity.qualitygate.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the MapStruct-generated {@code QualityGateDetectionMapperImpl} directly (in the same package so the
 * protected @Named helpers - sourceTypeToEntity/sourceTypeToDomain - are callable without reflection).
 */
class QualityGateDetectionMapperImplTest {

    private QualityGateDetectionMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new QualityGateDetectionMapperImpl();
        // findAndRegisterModules() picks up jackson-module-parameter-names (transitively provided by
        // spring-boot-starter-web), matching the auto-configured ObjectMapper used in production -
        // required to deserialize into constructor-only Lombok @Value classes like BranchProtection.
        mapper.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Nested
    class DomainToEntity {
        @Test
        void detectionToEntity_nullInput_returnsNull() {
            assertThat(mapper.detectionToEntity(null)).isNull();
        }

        @Test
        void detectionToEntity_mapsAllFields() {
            QualityGateDetection detection = QualityGateDetection.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE)
                    .sourceFile("pom.xml").sourceType(QualityGateDetection.SourceType.BUILD_TOOL)
                    .evidenceFound(List.of("pmd-plugin")).confidenceScore(0.7)
                    .triggersOnPR(true).associatedWorkflow("ci.yml").build();

            QGDetectionEntity entity = mapper.detectionToEntity(detection);

            assertThat(entity.getTool()).isEqualTo(QualityGateTool.PMD);
            assertThat(entity.getRelevantForThesis()).isTrue();
            assertThat(entity.getSourceType()).isEqualTo(QGDetectionEntity.SourceType.BUILD_TOOL);
            assertThat(entity.getEvidenceFound()).containsExactly("pmd-plugin");
        }

        @Test
        void workflowToEntity_nullInput_returnsNull() {
            assertThat(mapper.workflowToEntity(null)).isNull();
        }

        @Test
        void workflowToEntity_mapsToolsAsStrings() {
            QualityGateWorkflow workflow = QualityGateWorkflow.builder()
                    .workflowFile("ci.yml").workflowName("CI")
                    .tools(List.of(QualityGateTool.PMD, QualityGateTool.CHECKSTYLE))
                    .triggersOnPR(true).buildCommands(List.of("mvn test")).build();

            QGWorkflowEntity entity = mapper.workflowToEntity(workflow);

            assertThat(entity.getTools()).containsExactlyInAnyOrder("PMD", "CHECKSTYLE");
        }

        @Test
        void workflowRunToEntity_mapsIdAndCreatedAt() {
            WorkflowRun run = WorkflowRun.builder().id(42L).createdAt(Instant.parse("2024-01-01T00:00:00Z")).build();
            PRWorkflowRunEntity entity = mapper.workflowRunToEntity(run);
            assertThat(entity.getRunId()).isEqualTo(42L);
            assertThat(entity.getRunCreatedAt()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        }

        @Test
        void checkRunToEntity_mapsIdField() {
            CheckRun checkRun = CheckRun.builder().id(7L).matchedTool(QualityGateTool.PMD).build();
            PRCheckRunEntity entity = mapper.checkRunToEntity(checkRun);
            assertThat(entity.getCheckRunId()).isEqualTo(7L);
        }

        @Test
        void toolStatsToEntity_mapsEnforcementRate() {
            EnforcementDetectionResult.ToolStats stats = EnforcementDetectionResult.ToolStats.builder()
                    .tool(QualityGateTool.PMD).failures(2).enforced(1).bypassed(1).isRequiredCheck(true).build();
            EnforcementByToolEntity entity = mapper.toolStatsToEntity(stats);
            assertThat(entity.getEnforcementRate()).isEqualTo(0.5);
        }

        @Test
        void metadataToEntity_mapsTotalDurationToDetectionDuration() {
            QualityGateHistoryDetection.HistoryDetectionMetadata metadata =
                    QualityGateHistoryDetection.HistoryDetectionMetadata.builder()
                            .totalDurationMs(500).success(true).build();
            HistoryDetectionMetadataEntity entity = mapper.metadataToEntity(metadata);
            assertThat(entity.getDetectionDurationMs()).isEqualTo(500L);
        }

        @Test
        void repositoryResultToEntity_mapsScalarsAndToolSets() {
            RepositoryDetectionResult result = RepositoryDetectionResult.builder()
                    .owner("octocat").repo("hello-world").stars(5)
                    .requiredQGTools(Set.of(QualityGateTool.PMD))
                    .informationalQGTools(Set.of())
                    .build();

            RepositoryEntity entity = mapper.repositoryResultToEntity(result, 3);

            assertThat(entity.getOwner()).isEqualTo("octocat");
            assertThat(entity.getDetectionVersion()).isEqualTo(3);
            assertThat(entity.getIsCurrent()).isTrue();
            assertThat(entity.getRequiredQgTools()).containsExactly("PMD");
        }

        @Test
        void toolIntroductionToEntity_withEffectiveCommit_mapsAllDerivedFields() {
            CommitInfo commit = CommitInfo.builder().sha("abc").shortSha("abc").author("Alice")
                    .date(Instant.parse("2024-01-01T00:00:00Z")).message("init").build();
            QGToolIntroduction intro = QGToolIntroduction.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE)
                    .effectiveIntroductionCommit(commit).presentSinceRepoCreation(true).build();

            ToolIntroductionEntity entity = mapper.toolIntroductionToEntity(intro);

            assertThat(entity.getEffectiveSha()).isEqualTo("abc");
            assertThat(entity.getAuthor()).isEqualTo("Alice");
            assertThat(entity.getPresentSinceCreation()).isTrue();
        }

        @Test
        void toolIntroductionToEntity_withoutEffectiveCommit_derivedFieldsAreNull() {
            QGToolIntroduction intro = QGToolIntroduction.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE).build();
            ToolIntroductionEntity entity = mapper.toolIntroductionToEntity(intro);
            assertThat(entity.getEffectiveSha()).isNull();
        }

        @Test
        void fileIntroductionToEntity_withIntroducedAt() {
            CommitInfo commit = CommitInfo.builder().sha("s1").build();
            QGFileIntroduction fileIntro = QGFileIntroduction.builder()
                    .filePath("pom.xml").introducedAt(commit).presentSinceFileCreation(true).build();
            FileIntroductionEntity entity = mapper.fileIntroductionToEntity(fileIntro, "CONFIG");
            assertThat(entity.getIntroducedSha()).isEqualTo("s1");
            assertThat(entity.getIntroductionType()).isEqualTo("CONFIG");
        }

        @Test
        void enforcementToEntity_mapsScalars() {
            EnforcementDetectionResult enforcement = EnforcementDetectionResult.builder()
                    .status(EnforcementStatus.STRICTLY_ENFORCED).score(1.0).build();
            EnforcementEntity entity = mapper.enforcementToEntity(enforcement);
            assertThat(entity.getStatus()).isEqualTo(EnforcementStatus.STRICTLY_ENFORCED);
        }

        @Test
        void prSampleToEntity_mapsFailedToolsAsStrings() {
            PRDetectionResult pr = PRDetectionResult.builder()
                    .prNumber(5).failedQGTools(List.of(QualityGateTool.PMD)).build();
            PRSampleEntity entity = mapper.prSampleToEntity(pr);
            assertThat(entity.getFailedQgTools()).containsExactly("PMD");
        }
    }

    @Nested
    class EntityToDomain {
        @Test
        void detectionToDomain_missingConfidenceScore_defaultsToOne() {
            QGDetectionEntity entity = QGDetectionEntity.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE).build();
            QualityGateDetection domain = mapper.detectionToDomain(entity);
            assertThat(domain.getConfidenceScore()).isEqualTo(1.0);
        }

        @Test
        void detectionsToDomain_nullList_returnsEmptyList() {
            assertThat(mapper.detectionsToDomain(null)).isEmpty();
        }

        @Test
        void thesisRelevantDetectionsToDomain_filtersNonRelevant() {
            QGDetectionEntity relevant = QGDetectionEntity.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE).relevantForThesis(true).build();
            QGDetectionEntity notRelevant = QGDetectionEntity.builder()
                    .tool(QualityGateTool.SNYK).category(QualityGateCategory.SECURITY).relevantForThesis(false).build();
            List<QualityGateDetection> result = mapper.thesisRelevantDetectionsToDomain(List.of(relevant, notRelevant));
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getTool()).isEqualTo(QualityGateTool.PMD);
        }

        @Test
        void thesisRelevantDetectionsToDomain_nullRelevantFlag_isExcluded() {
            QGDetectionEntity nullFlag = QGDetectionEntity.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE).relevantForThesis(null).build();
            assertThat(mapper.thesisRelevantDetectionsToDomain(List.of(nullFlag))).isEmpty();
        }

        @Test
        void workflowToDomain_missingTriggersOnPR_defaultsFalse() {
            QGWorkflowEntity entity = QGWorkflowEntity.builder().workflowFile("ci.yml").build();
            assertThat(mapper.workflowToDomain(entity).isTriggersOnPR()).isFalse();
        }

        @Test
        void workflowsToDomain_nullList_returnsEmpty() {
            assertThat(mapper.workflowsToDomain(null)).isEmpty();
        }

        @Test
        void workflowRunToDomain_missingRunId_leavesDefaultId() {
            PRWorkflowRunEntity entity = PRWorkflowRunEntity.builder().workflowFile("ci.yml").build();
            WorkflowRun run = mapper.workflowRunToDomain(entity);
            assertThat(run.getId()).isZero();
        }

        @Test
        void workflowRunsToDomain_nullList_returnsEmpty() {
            assertThat(mapper.workflowRunsToDomain(null)).isEmpty();
        }

        @Test
        void checkRunToDomain_missingConfidence_defaultsZero() {
            PRCheckRunEntity entity = PRCheckRunEntity.builder().name("PMD").build();
            assertThat(mapper.checkRunToDomain(entity).getMatchConfidence()).isZero();
        }

        @Test
        void checkRunsToDomain_nullList_returnsEmpty() {
            assertThat(mapper.checkRunsToDomain(null)).isEmpty();
        }

        @Test
        void prSampleToDomain_missingBooleans_defaultToFalse() {
            PRSampleEntity entity = PRSampleEntity.builder().prNumber(1).build();
            PRDetectionResult result = mapper.prSampleToDomain(entity);
            assertThat(result.isMerged()).isFalse();
            assertThat(result.isHadWorkflowFailure()).isFalse();
            assertThat(result.isHadVerifiedQGFailure()).isFalse();
        }

        @Test
        void prSamplesToDomain_nullList_returnsEmpty() {
            assertThat(mapper.prSamplesToDomain(null)).isEmpty();
        }

        @Test
        void toolStatsToDomain_missingCounts_defaultToZero() {
            EnforcementByToolEntity entity = EnforcementByToolEntity.builder().tool(QualityGateTool.PMD).build();
            EnforcementDetectionResult.ToolStats stats = mapper.toolStatsToDomain(entity);
            assertThat(stats.getFailures()).isZero();
            assertThat(stats.isRequiredCheck()).isFalse();
        }

        @Test
        void enforcementToDomain_nullInput_returnsNull() {
            assertThat(mapper.enforcementToDomain(null)).isNull();
        }

        @Test
        void enforcementToDomain_mapsByToolAndDefaultsMissingCounts() {
            EnforcementByToolEntity toolEntity = EnforcementByToolEntity.builder().tool(QualityGateTool.PMD).build();
            EnforcementEntity entity = EnforcementEntity.builder()
                    .status(EnforcementStatus.STRICTLY_ENFORCED).byTool(List.of(toolEntity)).build();

            EnforcementDetectionResult result = mapper.enforcementToDomain(entity);

            assertThat(result.getTotalPRsChecked()).isZero();
            assertThat(result.getByTool()).containsKey(QualityGateTool.PMD);
        }

        @Test
        void metadataToDomain_missingFields_useDefaults() {
            HistoryDetectionMetadataEntity entity = HistoryDetectionMetadataEntity.builder().method("git-cli").build();
            QualityGateHistoryDetection.HistoryDetectionMetadata metadata = mapper.metadataToDomain(entity);
            assertThat(metadata.getTotalDurationMs()).isZero();
            assertThat(metadata.isSuccess()).isTrue();
        }

        @Test
        void fileIntroductionToDomain_nullEntity_returnsNull() {
            assertThat(mapper.fileIntroductionToDomain(null)).isNull();
        }

        @Test
        void fileIntroductionToDomain_withIntroducedSha_buildsCommitInfo() {
            FileIntroductionEntity entity = FileIntroductionEntity.builder()
                    .filePath("pom.xml").introducedSha("abc").presentSinceFileCreation(true).build();
            QGFileIntroduction result = mapper.fileIntroductionToDomain(entity);
            assertThat(result.getIntroducedAt().getSha()).isEqualTo("abc");
            assertThat(result.isPresentSinceFileCreation()).isTrue();
        }

        @Test
        void fileIntroductionToDomain_withoutIntroducedSha_hasNullCommitInfo() {
            FileIntroductionEntity entity = FileIntroductionEntity.builder().filePath("pom.xml").build();
            assertThat(mapper.fileIntroductionToDomain(entity).getIntroducedAt()).isNull();
        }

        @Test
        void toolIntroductionToDomain_nullEntity_returnsNull() {
            assertThat(mapper.toolIntroductionToDomain(null)).isNull();
        }

        @Test
        void toolIntroductionToDomain_groupsFileIntroductionsByType() {
            FileIntroductionEntity configFile = FileIntroductionEntity.builder()
                    .filePath("pom.xml").introductionType("CONFIG").build();
            FileIntroductionEntity ciFile = FileIntroductionEntity.builder()
                    .filePath("ci.yml").introductionType("CI").build();
            ToolIntroductionEntity entity = ToolIntroductionEntity.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE)
                    .effectiveSha("s1")
                    .fileIntroductions(List.of(configFile, ciFile)).build();

            QGToolIntroduction result = mapper.toolIntroductionToDomain(entity);

            assertThat(result.getConfigIntroductions()).hasSize(1);
            assertThat(result.getCiIntroductions()).hasSize(1);
            assertThat(result.getEffectiveIntroductionCommit().getSha()).isEqualTo("s1");
        }

        @Test
        void toolIntroductionToDomain_withoutEffectiveSha_hasNullEffectiveCommit() {
            ToolIntroductionEntity entity = ToolIntroductionEntity.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE).build();
            assertThat(mapper.toolIntroductionToDomain(entity).getEffectiveIntroductionCommit()).isNull();
        }
    }

    @Nested
    class JsonMappings {
        @Test
        void mapJsonToBranchProtection_nullInput_returnsNull() {
            assertThat(mapper.mapJsonToBranchProtection(null)).isNull();
        }

        @Test
        void mapJsonToBranchProtection_alreadyCorrectType_passesThrough() {
            BranchProtection bp = BranchProtection.builder().branch("main").build();
            assertThat(mapper.mapJsonToBranchProtection(bp)).isSameAs(bp);
        }

        @Test
        void mapJsonToBranchProtection_convertsFromMap() {
            Map<String, Object> json = Map.of("branch", "main", "isProtected", true);
            BranchProtection result = mapper.mapJsonToBranchProtection(json);
            assertThat(result.getBranch()).isEqualTo("main");
            assertThat(result.isProtected()).isTrue();
        }

        @Test
        void mapJsonToBranchProtection_invalidShape_returnsNullInsteadOfThrowing() {
            assertThat(mapper.mapJsonToBranchProtection("not-a-valid-shape-for-conversion")).isNull();
        }

        @Test
        void mapJsonToBranchProtectionInfo_passthroughAndConversion() {
            EnforcementDetectionResult.BranchProtectionInfo info =
                    EnforcementDetectionResult.BranchProtectionInfo.builder().defaultBranch("main").build();
            assertThat(mapper.mapJsonToBranchProtectionInfo(info)).isSameAs(info);
            assertThat(mapper.mapJsonToBranchProtectionInfo(null)).isNull();
            Map<String, Object> json = Map.of("defaultBranch", "develop");
            assertThat(mapper.mapJsonToBranchProtectionInfo(json).getDefaultBranch()).isEqualTo("develop");
        }

        @Test
        void mapJsonToFallbackInfo_passthroughAndConversion() {
            EnforcementDetectionResult.FallbackInfo info =
                    EnforcementDetectionResult.FallbackInfo.builder().likelyReason("x").build();
            assertThat(mapper.mapJsonToFallbackInfo(info)).isSameAs(info);
            assertThat(mapper.mapJsonToFallbackInfo(null)).isNull();
            Map<String, Object> json = Map.of("likelyReason", "y");
            assertThat(mapper.mapJsonToFallbackInfo(json).getLikelyReason()).isEqualTo("y");
        }
    }

    @Nested
    class ToolConversions {
        @Test
        void toolListToStringList_nullInput_returnsNull() {
            assertThat(mapper.toolListToStringList(null)).isNull();
        }

        @Test
        void toolListToStringList_convertsToNames() {
            assertThat(mapper.toolListToStringList(List.of(QualityGateTool.PMD))).containsExactly("PMD");
        }

        @Test
        void stringListToToolList_nullInput_returnsEmptyList() {
            assertThat(mapper.stringListToToolList(null)).isEmpty();
        }

        @Test
        void stringListToToolList_filtersInvalidNames() {
            List<QualityGateTool> result = mapper.stringListToToolList(List.of("PMD", "NOT_A_REAL_TOOL"));
            assertThat(result).containsExactly(QualityGateTool.PMD);
        }

        @Test
        void toolSetToStringSet_nullInput_returnsNull() {
            assertThat(mapper.toolSetToStringSet(null)).isNull();
        }

        @Test
        void toolSetToStringSet_convertsToNames() {
            assertThat(mapper.toolSetToStringSet(Set.of(QualityGateTool.PMD))).containsExactly("PMD");
        }

        @Test
        void stringSetToToolSet_nullInput_returnsEmptySet() {
            assertThat(mapper.stringSetToToolSet(null)).isEmpty();
        }

        @Test
        void stringSetToToolSet_filtersInvalidNames() {
            Set<QualityGateTool> result = mapper.stringSetToToolSet(Set.of("PMD", "BOGUS"));
            assertThat(result).containsExactly(QualityGateTool.PMD);
        }
    }

    @Nested
    class SourceTypeConversions {
        @Test
        void sourceTypeToEntity_nullInput_defaultsToConfigFile() {
            assertThat(mapper.sourceTypeToEntity(null)).isEqualTo(QGDetectionEntity.SourceType.CONFIG_FILE);
        }

        @Test
        void sourceTypeToEntity_mapsEachValue() {
            assertThat(mapper.sourceTypeToEntity(QualityGateDetection.SourceType.WORKFLOW_ACTION))
                    .isEqualTo(QGDetectionEntity.SourceType.WORKFLOW_ACTION);
            assertThat(mapper.sourceTypeToEntity(QualityGateDetection.SourceType.WORKFLOW_COMMAND))
                    .isEqualTo(QGDetectionEntity.SourceType.WORKFLOW_COMMAND);
            assertThat(mapper.sourceTypeToEntity(QualityGateDetection.SourceType.BUILD_TOOL))
                    .isEqualTo(QGDetectionEntity.SourceType.BUILD_TOOL);
            assertThat(mapper.sourceTypeToEntity(QualityGateDetection.SourceType.CONFIG_FILE))
                    .isEqualTo(QGDetectionEntity.SourceType.CONFIG_FILE);
        }

        @Test
        void sourceTypeToDomain_nullInput_defaultsToConfigFile() {
            assertThat(mapper.sourceTypeToDomain(null)).isEqualTo(QualityGateDetection.SourceType.CONFIG_FILE);
        }

        @Test
        void sourceTypeToDomain_checkRunAndCommitStatus_collapseToConfigFile() {
            assertThat(mapper.sourceTypeToDomain(QGDetectionEntity.SourceType.CHECK_RUN))
                    .isEqualTo(QualityGateDetection.SourceType.CONFIG_FILE);
            assertThat(mapper.sourceTypeToDomain(QGDetectionEntity.SourceType.COMMIT_STATUS))
                    .isEqualTo(QualityGateDetection.SourceType.CONFIG_FILE);
        }

        @Test
        void sourceTypeToDomain_mapsWorkflowAndBuildTool() {
            assertThat(mapper.sourceTypeToDomain(QGDetectionEntity.SourceType.WORKFLOW_ACTION))
                    .isEqualTo(QualityGateDetection.SourceType.WORKFLOW_ACTION);
            assertThat(mapper.sourceTypeToDomain(QGDetectionEntity.SourceType.WORKFLOW_COMMAND))
                    .isEqualTo(QualityGateDetection.SourceType.WORKFLOW_COMMAND);
            assertThat(mapper.sourceTypeToDomain(QGDetectionEntity.SourceType.BUILD_TOOL))
                    .isEqualTo(QualityGateDetection.SourceType.BUILD_TOOL);
        }
    }
}
