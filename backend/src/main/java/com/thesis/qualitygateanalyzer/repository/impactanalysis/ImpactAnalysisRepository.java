package com.thesis.qualitygateanalyzer.repository.impactanalysis;

import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Bulk delete executed immediately as its own DML statement, rather than the
     * default derived-delete's per-entity remove() (which Hibernate defers to flush
     * time, after pending inserts -- that ordering would collide with the unique
     * constraint on (owner, repo) during a force re-analysis of the same repo). Same
     * bug/fix as {@code QualityMetricSnapshotRepository.deleteByOwnerAndRepo}. Safe to
     * bypass JPA's cascade/orphanRemoval for the child snapshot/comparison rows here:
     * both child tables declare {@code ON DELETE CASCADE} at the database level
     * (V6__impact_analysis.sql), so Postgres removes them regardless of whether this
     * delete goes through the persistence context.
     */
    @Modifying
    @Query("delete from ImpactAnalysisEntity e where e.owner = :owner and e.repo = :repo")
    void deleteByOwnerAndRepo(@Param("owner") String owner, @Param("repo") String repo);

    List<ImpactAnalysisEntity> findByOrderByComputedAtDesc();
}
