package com.thesis.qualitygateanalyzer.service.qualitygate;

import com.thesis.qualitygateanalyzer.domain.qualitygate.RepositoryDetectionResult;

/**
 * Main orchestration service for repository quality gate detection.
 * <p>
 * Implements a multi-phase detection flow:
 * - Phase 1: Static configuration detection (workflow files, build configs, dedicated config files)
 * - Phase 2: Workflow runs detection (PR-related runs, failure detection)
 * - Phase 2.5: Check run and commit status verification (GitHub Actions and external apps)
 * - Phase 3: PR outcome detection (fixed, blocked, or merged with failure)
 * - Phase 4: Enforcement scoring
 * - Phase 5: Quality gate history detection (when tools were introduced via git blame)
 */
public interface QualityGateDetectionService {

    /**
     * Run the full detection pipeline for a repository.
     */
    RepositoryDetectionResult detect(String repoUrl);
}
