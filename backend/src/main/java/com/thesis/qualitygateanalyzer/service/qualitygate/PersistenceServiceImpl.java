package com.thesis.qualitygateanalyzer.service.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.*;
import com.thesis.qualitygateanalyzer.entity.qualitygate.EnforcementEntity;
import com.thesis.qualitygateanalyzer.entity.qualitygate.PRSampleEntity;
import com.thesis.qualitygateanalyzer.entity.qualitygate.RepositoryEntity;
import com.thesis.qualitygateanalyzer.entity.qualitygate.ToolIntroductionEntity;
import com.thesis.qualitygateanalyzer.mapper.QualityGateDetectionMapper;
import com.thesis.qualitygateanalyzer.repository.qualitygate.DetectedRepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Default {@link PersistenceService} implementation.
 * Uses MapStruct mapper for Domain Model ↔ Entity conversions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersistenceServiceImpl implements PersistenceService {

    private final DetectedRepositoryRepository repositoryRepository;
    private final QualityGateDetectionMapper mapper;

    // CHECK IF EXISTS

    @Override
    public boolean hasDetection(String owner, String repo) {
        return repositoryRepository.existsByOwnerAndRepoAndIsCurrentTrue(owner, repo);
    }

    // LOAD FROM DATABASE

    @Override
    @Transactional(readOnly = true)
    public Optional<RepositoryDetectionResult> loadDetection(String owner, String repo) {
        return repositoryRepository.findByOwnerAndRepoAndIsCurrentTrue(owner, repo)
                .map(this::entityToDomain);
    }

    // SAVE TO DATABASE

    @Override
    @Transactional
    public void saveDetection(RepositoryDetectionResult result, boolean forceNew) {
        String owner = result.getOwner();
        String repo = result.getRepo();

        log.info("Saving detection for {}/{} (forceNew={})", owner, repo, forceNew);

        int newVersion = 1;

        if (forceNew) {
            // Mark existing as not current and get next version
            repositoryRepository.markExistingAsNotCurrent(owner, repo);
            Integer maxVersion = repositoryRepository.findMaxVersionByOwnerAndRepo(owner, repo);
            newVersion = (maxVersion != null ? maxVersion : 0) + 1;
        } else {
            // Delete existing and create fresh
            repositoryRepository.findByOwnerAndRepoAndIsCurrentTrue(owner, repo)
                    .ifPresent(repositoryRepository::delete);
        }

        // Create new entity
        RepositoryEntity entity = domainToEntity(result, newVersion);
        repositoryRepository.save(entity);

        log.info("Detection saved for {}/{} as version {}", owner, repo, newVersion);
    }

    @Override
    @Transactional
    public void deleteDetection(String owner, String repo) {
        repositoryRepository.deleteByOwnerAndRepo(owner, repo);
        log.info("Deleted all detections for {}/{}", owner, repo);
    }

    // LIST OPERATIONS

    @Override
    @Transactional(readOnly = true)
    public List<RepositoryDetectionResult> listAllDetections() {
        return repositoryRepository.findByIsCurrentTrueOrderByDetectedAtDesc()
                .stream()
                .map(this::entityToDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepositoryDetectionResult> listWithQualityGate() {
        return repositoryRepository.findByIsCurrentTrueAndHasQualityGateTrueOrderByDetectedAtDesc()
                .stream()
                .map(this::entityToDomain)
                .collect(Collectors.toList());
    }

    // DOMAIN → ENTITY MAPPING (uses MapStruct mapper)

    private RepositoryEntity domainToEntity(RepositoryDetectionResult result, int version) {
        RepositoryEntity entity = mapper.repositoryResultToEntity(result, version);
        entity.setBranchProtection(result.getBranchProtection());

        // Map detections using mapper
        if (result.getAllDetections() != null) {
            for (QualityGateDetection detection : result.getAllDetections()) {
                entity.addDetection(mapper.detectionToEntity(detection));
            }
        }

        // Map workflows using mapper
        if (result.getQualityGateWorkflows() != null) {
            for (QualityGateWorkflow workflow : result.getQualityGateWorkflows()) {
                entity.addWorkflow(mapper.workflowToEntity(workflow));
            }
        }

        // Map tool introductions
        if (result.getQualityGateHistory() != null) {
            QualityGateHistoryDetection history = result.getQualityGateHistory();

            // Set first commit info
            if (history.getRepoFirstCommit() != null) {
                entity.setFirstCommitSha(history.getRepoFirstCommit().getSha());
                entity.setFirstCommitDate(history.getRepoFirstCommit().getDate());
            }
            entity.setTotalCommits(history.getTotalRepoCommits());

            // Map tool introductions
            if (history.getToolIntroductions() != null) {
                for (QGToolIntroduction intro : history.getToolIntroductions()) {
                    entity.addToolIntroduction(mapToolIntroductionToEntity(intro));
                }
            }

            // Map history metadata using mapper
            if (history.getMetadata() != null) {
                entity.setHistoryMetadata(mapper.metadataToEntity(history.getMetadata()));
            }
        }

        // Map enforcement
        if (result.getEnforcement() != null) {
            entity.setEnforcement(mapEnforcementToEntity(result.getEnforcement()));
        }

        return entity;
    }

    /**
     * Map QGToolIntroduction to entity (handles nested file introductions).
     */
    private ToolIntroductionEntity mapToolIntroductionToEntity(QGToolIntroduction intro) {
        ToolIntroductionEntity entity = mapper.toolIntroductionToEntity(intro);

        // Map config introductions
        if (intro.getConfigIntroductions() != null) {
            for (QGFileIntroduction fileIntro : intro.getConfigIntroductions()) {
                entity.addFileIntroduction(mapper.fileIntroductionToEntity(fileIntro, "CONFIG"));
            }
        }

        // Map CI introductions
        if (intro.getCiIntroductions() != null) {
            for (QGFileIntroduction fileIntro : intro.getCiIntroductions()) {
                entity.addFileIntroduction(mapper.fileIntroductionToEntity(fileIntro, "CI"));
            }
        }

        return entity;
    }

    /**
     * Map EnforcementDetectionResult to entity (handles nested collections).
     */
    private EnforcementEntity mapEnforcementToEntity(EnforcementDetectionResult enforcement) {
        EnforcementEntity entity = mapper.enforcementToEntity(enforcement);
        entity.setBranchProtectionInfo(enforcement.getBranchProtection());
        entity.setFallbackInfo(enforcement.getFallbackInfo());

        // Map by-tool stats using mapper
        if (enforcement.getByTool() != null) {
            for (Map.Entry<QualityGateTool, EnforcementDetectionResult.ToolStats> entry :
                    enforcement.getByTool().entrySet()) {
                entity.addByTool(mapper.toolStatsToEntity(entry.getValue()));
            }
        }

        // Map sample PRs
        if (enforcement.getSamplePRs() != null) {
            for (PRDetectionResult pr : enforcement.getSamplePRs()) {
                entity.addSamplePR(mapPRSampleToEntity(pr));
            }
        }

        return entity;
    }

    /**
     * Map PRDetectionResult to entity (handles nested workflow/check runs).
     */
    private PRSampleEntity mapPRSampleToEntity(PRDetectionResult pr) {
        PRSampleEntity entity = mapper.prSampleToEntity(pr);

        // Map workflow runs using mapper
        if (pr.getWorkflowRuns() != null) {
            for (WorkflowRun run : pr.getWorkflowRuns()) {
                entity.addWorkflowRun(mapper.workflowRunToEntity(run));
            }
        }

        // Map check runs using mapper
        if (pr.getQualityGateCheckRuns() != null) {
            for (CheckRun checkRun : pr.getQualityGateCheckRuns()) {
                entity.addCheckRun(mapper.checkRunToEntity(checkRun));
            }
        }

        return entity;
    }

    // ENTITY → DOMAIN MAPPING (uses MapStruct mapper)

    private RepositoryDetectionResult entityToDomain(RepositoryEntity entity) {
        return RepositoryDetectionResult.builder()
                .owner(entity.getOwner())
                .repo(entity.getRepo())
                .url(entity.getUrl())
                .description(entity.getDescription())
                .primaryLanguage(entity.getPrimaryLanguage())
                .defaultBranch(entity.getDefaultBranch())
                .stars(entity.getStars() != null ? entity.getStars() : 0)
                .forks(entity.getForks() != null ? entity.getForks() : 0)
                .hasQualityGate(entity.getHasQualityGate() != null && entity.getHasQualityGate())
                .allDetections(mapper.detectionsToDomain(entity.getDetections()))
                .thesisRelevantDetections(mapper.thesisRelevantDetectionsToDomain(entity.getDetections()))
                .qualityGateWorkflows(mapper.workflowsToDomain(entity.getWorkflows()))
                .branchProtection(mapper.mapJsonToBranchProtection(entity.getBranchProtection()))
                .requiredQGTools(mapper.stringSetToToolSet(entity.getRequiredQgTools()))
                .informationalQGTools(mapper.stringSetToToolSet(entity.getInformationalQgTools()))
                .enforcement(mapper.enforcementToDomain(entity.getEnforcement()))
                .qualityGateHistory(mapHistoryToDomain(entity))
                .conclusion(entity.getConclusion())
                .recommendation(entity.getRecommendation())
                .detectionTimeMs(entity.getDetectionTimeMs() != null ? entity.getDetectionTimeMs() : 0)
                .detectedAt(entity.getDetectedAt())
                .apiCallsMade(entity.getApiCallsMade() != null ? entity.getApiCallsMade() : 0)
                .build();
    }

    /**
     * Map history detection from entity (computes earliest/latest introductions).
     */
    private QualityGateHistoryDetection mapHistoryToDomain(RepositoryEntity entity) {
        if (entity.getToolIntroductions() == null || entity.getToolIntroductions().isEmpty()) {
            return null;
        }

        List<QGToolIntroduction> introductions = entity.getToolIntroductions().stream()
                .map(mapper::toolIntroductionToDomain)
                .collect(Collectors.toList());

        // Find earliest and latest
        QGToolIntroduction earliest = introductions.stream()
                .filter(i -> i.getEffectiveDate() != null)
                .min(Comparator.comparing(QGToolIntroduction::getEffectiveDate))
                .orElse(null);

        QGToolIntroduction latest = introductions.stream()
                .filter(i -> i.getEffectiveDate() != null)
                .max(Comparator.comparing(QGToolIntroduction::getEffectiveDate))
                .orElse(null);

        CommitInfo firstCommit = null;
        if (entity.getFirstCommitSha() != null) {
            firstCommit = CommitInfo.builder()
                    .sha(entity.getFirstCommitSha())
                    .date(entity.getFirstCommitDate())
                    .build();
        }

        QualityGateHistoryDetection.HistoryDetectionMetadata metadata = null;
        if (entity.getHistoryMetadata() != null) {
            metadata = mapper.metadataToDomain(entity.getHistoryMetadata());
        }

        return QualityGateHistoryDetection.builder()
                .toolIntroductions(introductions)
                .earliestIntroduction(earliest)
                .latestIntroduction(latest)
                .repoFirstCommit(firstCommit)
                .totalRepoCommits(entity.getTotalCommits() != null ? entity.getTotalCommits() : 0)
                .metadata(metadata)
                .build();
    }
}
