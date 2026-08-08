package com.thesis.qualitygateanalyzer.repository.impactanalysis;

import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for E2E quality impact analyses.
 */
@Repository
public interface ImpactAnalysisRepository extends JpaRepository<ImpactAnalysisEntity, UUID> {

    Optional<ImpactAnalysisEntity> findByOwnerAndRepo(String owner, String repo);

    boolean existsByOwnerAndRepo(String owner, String repo);

    void deleteByOwnerAndRepo(String owner, String repo);

    List<ImpactAnalysisEntity> findByOrderByComputedAtDesc();
}
