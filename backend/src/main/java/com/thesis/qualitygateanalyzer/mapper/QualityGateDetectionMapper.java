package com.thesis.qualitygateanalyzer.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.*;
import com.thesis.qualitygateanalyzer.entity.qualitygate.*;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MapStruct mapper for Entity ↔ Domain conversions.
 * <p>
 * Uses abstract class to allow injection of ObjectMapper for JSONB handling.
 */
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class QualityGateDetectionMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    // DOMAIN → ENTITY MAPPINGS

    // --- QualityGateDetection → QGDetectionEntity ---
    @Mapping(target = "repository", ignore = true)
    @Mapping(target = "relevantForThesis", source = "relevantForThesis")
    @Mapping(target = "sourceType", source = "sourceType", qualifiedByName = "sourceTypeToEntity")
    public abstract QGDetectionEntity detectionToEntity(QualityGateDetection detection);

    @Named("sourceTypeToEntity")
    protected QGDetectionEntity.SourceType sourceTypeToEntity(QualityGateDetection.SourceType sourceType) {
        if (sourceType == null) return QGDetectionEntity.SourceType.CONFIG_FILE;
        return switch (sourceType) {
            case WORKFLOW_ACTION -> QGDetectionEntity.SourceType.WORKFLOW_ACTION;
            case WORKFLOW_COMMAND -> QGDetectionEntity.SourceType.WORKFLOW_COMMAND;
            case BUILD_TOOL -> QGDetectionEntity.SourceType.BUILD_TOOL;
            case CONFIG_FILE -> QGDetectionEntity.SourceType.CONFIG_FILE;
        };
    }

    // --- QualityGateWorkflow → QGWorkflowEntity ---
    @Mapping(target = "repository", ignore = true)
    @Mapping(target = "tools", source = "tools", qualifiedByName = "toolListToStringList")
    public abstract QGWorkflowEntity workflowToEntity(QualityGateWorkflow workflow);

    // --- WorkflowRun → PRWorkflowRunEntity ---
    @Mapping(target = "prSample", ignore = true)
    @Mapping(target = "runId", source = "id")
    @Mapping(target = "runCreatedAt", source = "createdAt")
    public abstract PRWorkflowRunEntity workflowRunToEntity(WorkflowRun run);

    // --- CheckRun → PRCheckRunEntity ---
    @Mapping(target = "prSample", ignore = true)
    @Mapping(target = "checkRunId", source = "id")
    public abstract PRCheckRunEntity checkRunToEntity(CheckRun checkRun);

    // --- EnforcementDetectionResult.ToolStats → EnforcementByToolEntity ---
    @Mapping(target = "enforcement", ignore = true)
    public abstract EnforcementByToolEntity toolStatsToEntity(EnforcementDetectionResult.ToolStats stats);

    // --- HistoryDetectionMetadata → HistoryDetectionMetadataEntity ---
    @Mapping(target = "repository", ignore = true)
    @Mapping(target = "detectionDurationMs", source = "totalDurationMs")
    public abstract HistoryDetectionMetadataEntity metadataToEntity(
            QualityGateHistoryDetection.HistoryDetectionMetadata metadata);

    // --- RepositoryDetectionResult → RepositoryEntity (top-level scalars only; relationship
    //     collections and history-derived fields are wired by PersistenceServiceImpl, since
    //     they require cascade-safe add*() calls and conditional nested-source data) ---
    @Mapping(target = "detectionVersion", source = "version")
    @Mapping(target = "isCurrent", constant = "true")
    @Mapping(target = "requiredQgTools", source = "result.requiredQGTools", qualifiedByName = "toolSetToStringSet")
    @Mapping(target = "informationalQgTools", source = "result.informationalQGTools", qualifiedByName = "toolSetToStringSet")
    @Mapping(target = "branchProtection", ignore = true)
    @Mapping(target = "firstCommitSha", ignore = true)
    @Mapping(target = "firstCommitDate", ignore = true)
    @Mapping(target = "totalCommits", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "detections", ignore = true)
    @Mapping(target = "workflows", ignore = true)
    @Mapping(target = "toolIntroductions", ignore = true)
    @Mapping(target = "enforcement", ignore = true)
    @Mapping(target = "historyMetadata", ignore = true)
    public abstract RepositoryEntity repositoryResultToEntity(RepositoryDetectionResult result, int version);

    // --- QGToolIntroduction → ToolIntroductionEntity (top-level scalars only; the file
    //     introduction list is wired by PersistenceServiceImpl via addFileIntroduction()) ---
    @Mapping(target = "presentSinceCreation", source = "presentSinceRepoCreation")
    @Mapping(target = "effectiveSha", source = "effectiveIntroductionCommit.sha")
    @Mapping(target = "effectiveShortSha", source = "effectiveIntroductionCommit.shortSha")
    @Mapping(target = "effectiveDate", source = "effectiveIntroductionCommit.date")
    @Mapping(target = "author", source = "effectiveIntroductionCommit.author")
    @Mapping(target = "commitMessage", source = "effectiveIntroductionCommit.message")
    @Mapping(target = "repository", ignore = true)
    @Mapping(target = "detectionMethod", ignore = true)
    @Mapping(target = "filePath", ignore = true)
    @Mapping(target = "searchPattern", ignore = true)
    @Mapping(target = "fileIntroductions", ignore = true)
    public abstract ToolIntroductionEntity toolIntroductionToEntity(QGToolIntroduction intro);

    // --- QGFileIntroduction → FileIntroductionEntity ---
    @Mapping(target = "introductionType", source = "type")
    @Mapping(target = "filePath", source = "fileIntro.filePath")
    @Mapping(target = "searchPattern", source = "fileIntro.searchPattern")
    @Mapping(target = "presentSinceFileCreation", source = "fileIntro.presentSinceFileCreation")
    @Mapping(target = "introducedSha", source = "fileIntro.introducedAt.sha")
    @Mapping(target = "introducedShortSha", source = "fileIntro.introducedAt.shortSha")
    @Mapping(target = "introducedDate", source = "fileIntro.introducedAt.date")
    @Mapping(target = "introducedAuthor", source = "fileIntro.introducedAt.author")
    @Mapping(target = "introducedMessage", source = "fileIntro.introducedAt.message")
    @Mapping(target = "toolIntroduction", ignore = true)
    @Mapping(target = "allOccurrences", ignore = true)
    public abstract FileIntroductionEntity fileIntroductionToEntity(QGFileIntroduction fileIntro, String type);

    // --- EnforcementDetectionResult → EnforcementEntity (top-level scalars only; byTool and
    //     samplePRs collections are wired by PersistenceServiceImpl via addByTool()/addSamplePR()) ---
    @Mapping(target = "totalPrsChecked", source = "totalPRsChecked")
    @Mapping(target = "prsWithQgFailures", source = "prsWithQGFailures")
    @Mapping(target = "branchProtectionInfo", ignore = true)
    @Mapping(target = "fallbackInfo", ignore = true)
    @Mapping(target = "repository", ignore = true)
    @Mapping(target = "byTool", ignore = true)
    @Mapping(target = "samplePRs", ignore = true)
    public abstract EnforcementEntity enforcementToEntity(EnforcementDetectionResult enforcement);

    // --- PRDetectionResult → PRSampleEntity (top-level scalars only; workflowRuns and checkRuns
    //     collections are wired by PersistenceServiceImpl via addWorkflowRun()/addCheckRun()) ---
    @Mapping(target = "prCreatedAt", source = "createdAt")
    @Mapping(target = "prClosedAt", source = "closedAt")
    @Mapping(target = "prMergedAt", source = "mergedAt")
    @Mapping(target = "lastWorkflowPassed", source = "lastWorkflowRunPassed")
    @Mapping(target = "hadVerifiedQgFailure", source = "hadVerifiedQGFailure")
    @Mapping(target = "lastQgCheckPassed", source = "lastQGCheckPassed")
    @Mapping(target = "failedQgTools", source = "failedQGTools", qualifiedByName = "toolListToStringList")
    @Mapping(target = "enforcement", ignore = true)
    @Mapping(target = "workflowRuns", ignore = true)
    @Mapping(target = "checkRuns", ignore = true)
    public abstract PRSampleEntity prSampleToEntity(PRDetectionResult pr);

    // ENTITY → DOMAIN MAPPINGS

    // --- QGDetectionEntity → QualityGateDetection ---
    @Mapping(target = "sourceType", source = "sourceType", qualifiedByName = "sourceTypeToDomain")
    @Mapping(target = "confidenceScore", source = "confidenceScore", defaultValue = "1.0")
    public abstract QualityGateDetection detectionToDomain(QGDetectionEntity entity);

    public List<QualityGateDetection> detectionsToDomain(List<QGDetectionEntity> entities) {
        if (entities == null) return List.of();
        return entities.stream()
                .map(this::detectionToDomain)
                .collect(Collectors.toList());
    }

    public List<QualityGateDetection> thesisRelevantDetectionsToDomain(List<QGDetectionEntity> entities) {
        if (entities == null) return List.of();
        return entities.stream()
                .filter(e -> e.getRelevantForThesis() != null && e.getRelevantForThesis())
                .map(this::detectionToDomain)
                .collect(Collectors.toList());
    }

    @Named("sourceTypeToDomain")
    protected QualityGateDetection.SourceType sourceTypeToDomain(QGDetectionEntity.SourceType sourceType) {
        if (sourceType == null) return QualityGateDetection.SourceType.CONFIG_FILE;
        return switch (sourceType) {
            case WORKFLOW_ACTION -> QualityGateDetection.SourceType.WORKFLOW_ACTION;
            case WORKFLOW_COMMAND -> QualityGateDetection.SourceType.WORKFLOW_COMMAND;
            case BUILD_TOOL -> QualityGateDetection.SourceType.BUILD_TOOL;
            case CONFIG_FILE, CHECK_RUN, COMMIT_STATUS -> QualityGateDetection.SourceType.CONFIG_FILE;
        };
    }

    // --- QGWorkflowEntity → QualityGateWorkflow ---
    @Mapping(target = "tools", source = "tools", qualifiedByName = "stringListToToolList")
    @Mapping(target = "triggersOnPR", source = "triggersOnPR", defaultValue = "false")
    public abstract QualityGateWorkflow workflowToDomain(QGWorkflowEntity entity);

    public List<QualityGateWorkflow> workflowsToDomain(List<QGWorkflowEntity> entities) {
        if (entities == null) return List.of();
        return entities.stream()
                .map(this::workflowToDomain)
                .collect(Collectors.toList());
    }

    // --- PRWorkflowRunEntity → WorkflowRun ---
    @Mapping(target = "id", source = "runId")
    @Mapping(target = "createdAt", source = "runCreatedAt")
    @Mapping(target = "prNumber", ignore = true) // Not stored in entity
    public abstract WorkflowRun workflowRunToDomain(PRWorkflowRunEntity entity);

    public List<WorkflowRun> workflowRunsToDomain(List<PRWorkflowRunEntity> entities) {
        if (entities == null) return List.of();
        return entities.stream()
                .map(this::workflowRunToDomain)
                .collect(Collectors.toList());
    }

    // --- PRCheckRunEntity → CheckRun ---
    @Mapping(target = "id", source = "checkRunId")
    @Mapping(target = "matchConfidence", source = "matchConfidence", defaultValue = "0.0")
    public abstract CheckRun checkRunToDomain(PRCheckRunEntity entity);

    public List<CheckRun> checkRunsToDomain(List<PRCheckRunEntity> entities) {
        if (entities == null) return List.of();
        return entities.stream()
                .map(this::checkRunToDomain)
                .collect(Collectors.toList());
    }

    // --- PRSampleEntity → PRDetectionResult ---
    @Mapping(target = "prNumber", source = "prNumber")
    @Mapping(target = "prTitle", source = "prTitle")
    @Mapping(target = "prUrl", source = "prUrl")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "merged", source = "merged", defaultValue = "false")
    @Mapping(target = "createdAt", source = "prCreatedAt")
    @Mapping(target = "closedAt", source = "prClosedAt")
    @Mapping(target = "mergedAt", source = "prMergedAt")
    @Mapping(target = "hadWorkflowFailure", source = "hadWorkflowFailure", defaultValue = "false")
    @Mapping(target = "lastWorkflowRunPassed", source = "lastWorkflowPassed", defaultValue = "false")
    @Mapping(target = "hadVerifiedQGFailure", source = "hadVerifiedQgFailure", defaultValue = "false")
    @Mapping(target = "lastQGCheckPassed", source = "lastQgCheckPassed", defaultValue = "false")
    @Mapping(target = "failedQGTools", source = "failedQgTools", qualifiedByName = "stringListToToolList")
    @Mapping(target = "failureMessages", source = "failureMessages")
    @Mapping(target = "qgWasRequiredCheck", source = "qgWasRequiredCheck", defaultValue = "false")
    @Mapping(target = "outcome", source = "outcome")
    @Mapping(target = "outcomeReason", source = "outcomeReason")
    @Mapping(target = "workflowRuns", source = "workflowRuns")
    @Mapping(target = "qualityGateCheckRuns", source = "checkRuns")
    public abstract PRDetectionResult prSampleToDomain(PRSampleEntity entity);

    public List<PRDetectionResult> prSamplesToDomain(List<PRSampleEntity> entities) {
        if (entities == null) return List.of();
        return entities.stream()
                .map(this::prSampleToDomain)
                .collect(Collectors.toList());
    }

    // --- EnforcementByToolEntity → EnforcementDetectionResult.ToolStats ---
    @Mapping(target = "failures", source = "failures", defaultValue = "0")
    @Mapping(target = "enforced", source = "enforced", defaultValue = "0")
    @Mapping(target = "bypassed", source = "bypassed", defaultValue = "0")
    @Mapping(target = "isRequiredCheck", source = "isRequiredCheck", defaultValue = "false")
    public abstract EnforcementDetectionResult.ToolStats toolStatsToDomain(EnforcementByToolEntity entity);

    // --- EnforcementEntity → EnforcementDetectionResult ---
    public EnforcementDetectionResult enforcementToDomain(EnforcementEntity entity) {
        if (entity == null) return null;

        Map<QualityGateTool, EnforcementDetectionResult.ToolStats> byToolMap = new HashMap<>();
        if (entity.getByTool() != null) {
            for (EnforcementByToolEntity toolEntity : entity.getByTool()) {
                byToolMap.put(toolEntity.getTool(), toolStatsToDomain(toolEntity));
            }
        }

        return EnforcementDetectionResult.builder()
                .status(entity.getStatus())
                .score(entity.getScore())
                .confidence(entity.getConfidence())
                .totalPRsChecked(entity.getTotalPrsChecked() != null ? entity.getTotalPrsChecked() : 0)
                .prsWithQGFailures(entity.getPrsWithQgFailures() != null ? entity.getPrsWithQgFailures() : 0)
                .fixedThenMerged(entity.getFixedThenMerged() != null ? entity.getFixedThenMerged() : 0)
                .blocked(entity.getBlocked() != null ? entity.getBlocked() : 0)
                .mergedWithFailure(entity.getMergedWithFailure() != null ? entity.getMergedWithFailure() : 0)
                .stillOpen(entity.getStillOpen() != null ? entity.getStillOpen() : 0)
                .byTool(byToolMap)
                .samplePRs(prSamplesToDomain(entity.getSamplePRs()))
                .branchProtection(mapJsonToBranchProtectionInfo(entity.getBranchProtectionInfo()))
                .fallbackInfo(mapJsonToFallbackInfo(entity.getFallbackInfo()))
                .interpretation(entity.getInterpretation())
                .build();
    }

    // --- HistoryDetectionMetadataEntity → HistoryDetectionMetadata ---
    @Mapping(target = "totalDurationMs", source = "detectionDurationMs", defaultValue = "0L")
    @Mapping(target = "filesScanned", source = "filesScanned", defaultValue = "0")
    @Mapping(target = "toolsScanned", source = "toolsScanned", defaultValue = "0")
    @Mapping(target = "success", source = "success", defaultValue = "true")
    public abstract QualityGateHistoryDetection.HistoryDetectionMetadata metadataToDomain(
            HistoryDetectionMetadataEntity entity);

    // --- FileIntroductionEntity → QGFileIntroduction ---
    public QGFileIntroduction fileIntroductionToDomain(FileIntroductionEntity entity) {
        if (entity == null) return null;

        CommitInfo introducedAt = null;
        if (entity.getIntroducedSha() != null) {
            introducedAt = CommitInfo.builder()
                    .sha(entity.getIntroducedSha())
                    .shortSha(entity.getIntroducedShortSha())
                    .date(entity.getIntroducedDate())
                    .author(entity.getIntroducedAuthor())
                    .message(entity.getIntroducedMessage())
                    .build();
        }

        return QGFileIntroduction.builder()
                .filePath(entity.getFilePath())
                .searchPattern(entity.getSearchPattern())
                .introducedAt(introducedAt)
                .presentSinceFileCreation(entity.getPresentSinceFileCreation() != null
                        && entity.getPresentSinceFileCreation())
                .build();
    }

    // --- ToolIntroductionEntity → QGToolIntroduction ---
    public QGToolIntroduction toolIntroductionToDomain(ToolIntroductionEntity entity) {
        if (entity == null) return null;

        CommitInfo effectiveCommit = null;
        if (entity.getEffectiveSha() != null) {
            effectiveCommit = CommitInfo.builder()
                    .sha(entity.getEffectiveSha())
                    .shortSha(entity.getEffectiveShortSha())
                    .date(entity.getEffectiveDate())
                    .author(entity.getAuthor())
                    .message(entity.getCommitMessage())
                    .build();
        }

        List<QGFileIntroduction> configIntros = new ArrayList<>();
        List<QGFileIntroduction> ciIntros = new ArrayList<>();

        if (entity.getFileIntroductions() != null) {
            for (FileIntroductionEntity fileEntity : entity.getFileIntroductions()) {
                QGFileIntroduction fileIntro = fileIntroductionToDomain(fileEntity);
                if ("CONFIG".equals(fileEntity.getIntroductionType())) {
                    configIntros.add(fileIntro);
                } else {
                    ciIntros.add(fileIntro);
                }
            }
        }

        return QGToolIntroduction.builder()
                .tool(entity.getTool())
                .category(entity.getCategory())
                .configIntroductions(configIntros)
                .ciIntroductions(ciIntros)
                .effectiveIntroductionCommit(effectiveCommit)
                .effectiveDate(entity.getEffectiveDate())
                .presentSinceRepoCreation(entity.getPresentSinceCreation() != null
                        && entity.getPresentSinceCreation())
                .introductionSummary(entity.getIntroductionSummary())
                .build();
    }

    // JSONB MAPPINGS (using Jackson ObjectMapper)

    /**
     * Map JSONB Object to BranchProtection domain object.
     */
    @SuppressWarnings("unchecked")
    public BranchProtection mapJsonToBranchProtection(Object json) {
        if (json == null) return null;
        try {
            if (json instanceof BranchProtection) {
                return (BranchProtection) json;
            }
            return objectMapper.convertValue(json, BranchProtection.class);
        } catch (Exception e) {
            // Log but don't fail - return null if conversion fails
            return null;
        }
    }

    /**
     * Map JSONB Object to BranchProtectionInfo domain object.
     */
    @SuppressWarnings("unchecked")
    public EnforcementDetectionResult.BranchProtectionInfo mapJsonToBranchProtectionInfo(Object json) {
        if (json == null) return null;
        try {
            if (json instanceof EnforcementDetectionResult.BranchProtectionInfo) {
                return (EnforcementDetectionResult.BranchProtectionInfo) json;
            }
            return objectMapper.convertValue(json, EnforcementDetectionResult.BranchProtectionInfo.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Map JSONB Object to FallbackInfo domain object.
     */
    @SuppressWarnings("unchecked")
    public EnforcementDetectionResult.FallbackInfo mapJsonToFallbackInfo(Object json) {
        if (json == null) return null;
        try {
            if (json instanceof EnforcementDetectionResult.FallbackInfo) {
                return (EnforcementDetectionResult.FallbackInfo) json;
            }
            return objectMapper.convertValue(json, EnforcementDetectionResult.FallbackInfo.class);
        } catch (Exception e) {
            return null;
        }
    }

    // TOOL CONVERSIONS

    @Named("toolListToStringList")
    public List<String> toolListToStringList(List<QualityGateTool> tools) {
        if (tools == null) return null;
        return tools.stream()
                .map(QualityGateTool::name)
                .collect(Collectors.toList());
    }

    @Named("stringListToToolList")
    public List<QualityGateTool> stringListToToolList(List<String> strings) {
        if (strings == null) return List.of();
        return strings.stream()
                .map(name -> {
                    try {
                        return QualityGateTool.valueOf(name);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Named("toolSetToStringSet")
    public Set<String> toolSetToStringSet(Set<QualityGateTool> tools) {
        if (tools == null) return null;
        return tools.stream()
                .map(QualityGateTool::name)
                .collect(Collectors.toSet());
    }

    public Set<QualityGateTool> stringSetToToolSet(Set<String> strings) {
        if (strings == null) return Set.of();
        return strings.stream()
                .map(name -> {
                    try {
                        return QualityGateTool.valueOf(name);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
