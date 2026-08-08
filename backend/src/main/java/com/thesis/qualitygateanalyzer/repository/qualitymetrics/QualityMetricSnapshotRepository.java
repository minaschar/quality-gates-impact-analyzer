package com.thesis.qualitygateanalyzer.repository.qualitymetrics;

import com.thesis.qualitygateanalyzer.entity.qualitymetrics.QualityMetricSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QualityMetricSnapshotRepository extends JpaRepository<QualityMetricSnapshotEntity, UUID> {

    List<QualityMetricSnapshotEntity> findByOwnerAndRepoOrderByPullRequestNumberAscAnalysisRoleAsc(
            String owner, String repo);

    boolean existsByOwnerAndRepo(String owner, String repo);

    /**
     * Bulk delete executed immediately as its own DML statement, rather than the
     * default derived-delete's per-entity remove() (which Hibernate defers to flush
     * time, after pending inserts — that ordering would collide with the unique
     * constraint on source_row_id during a force re-ingest of the same rows).
     */
    @Modifying
    @Query("delete from QualityMetricSnapshotEntity e where e.owner = :owner and e.repo = :repo")
    void deleteByOwnerAndRepo(@Param("owner") String owner, @Param("repo") String repo);
}
