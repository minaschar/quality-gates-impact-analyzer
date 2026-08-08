package com.thesis.qualitygateanalyzer.repository.qualitygate;

import com.thesis.qualitygateanalyzer.entity.qualitygate.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for analyzed GitHub repositories.
 */
@Repository
public interface DetectedRepositoryRepository extends JpaRepository<RepositoryEntity, UUID> {

    // BASIC QUERIES

    /**
     * Find current detection for a repository.
     */
    Optional<RepositoryEntity> findByOwnerAndRepoAndIsCurrentTrue(String owner, String repo);

    /**
     * Check if repository has been analyzed.
     */
    boolean existsByOwnerAndRepoAndIsCurrentTrue(String owner, String repo);

    /**
     * Find latest version number for a repository.
     */
    @Query("SELECT COALESCE(MAX(r.detectionVersion), 0) FROM RepositoryEntity r " +
            "WHERE r.owner = :owner AND r.repo = :repo")
    Integer findMaxVersionByOwnerAndRepo(@Param("owner") String owner, @Param("repo") String repo);

    // LISTING QUERIES

    /**
     * Find all current detections.
     */
    List<RepositoryEntity> findByIsCurrentTrueOrderByDetectedAtDesc();

    /**
     * Find all repositories with quality gate.
     */
    List<RepositoryEntity> findByIsCurrentTrueAndHasQualityGateTrueOrderByDetectedAtDesc();

    // UPDATE QUERIES

    /**
     * Mark all existing detections for a repo as not current.
     */
    @Modifying
    @Query("UPDATE RepositoryEntity r SET r.isCurrent = false " +
            "WHERE r.owner = :owner AND r.repo = :repo AND r.isCurrent = true")
    void markExistingAsNotCurrent(@Param("owner") String owner, @Param("repo") String repo);

    // DELETE QUERIES

    /**
     * Delete all detections for a repository.
     */
    void deleteByOwnerAndRepo(String owner, String repo);
}
