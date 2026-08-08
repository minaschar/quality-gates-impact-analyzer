package com.thesis.qualitygateanalyzer.service.qualitygate;

import com.thesis.qualitygateanalyzer.domain.qualitygate.RepositoryDetectionResult;

import java.util.List;
import java.util.Optional;

/**
 * Service for persisting and retrieving quality gate detection results.
 */
public interface PersistenceService {

    /**
     * Check if a repository has been detected (current version exists).
     */
    boolean hasDetection(String owner, String repo);

    /**
     * Load current detection result from database.
     */
    Optional<RepositoryDetectionResult> loadDetection(String owner, String repo);

    /**
     * Save detection result to database.
     * If forceNew is true, creates a new version. Otherwise, upserts.
     */
    void saveDetection(RepositoryDetectionResult result, boolean forceNew);

    /**
     * Delete all detections for a repository.
     */
    void deleteDetection(String owner, String repo);

    /**
     * Get all detected repositories (current versions only).
     */
    List<RepositoryDetectionResult> listAllDetections();

    /**
     * Get repositories with quality gate.
     */
    List<RepositoryDetectionResult> listWithQualityGate();
}
