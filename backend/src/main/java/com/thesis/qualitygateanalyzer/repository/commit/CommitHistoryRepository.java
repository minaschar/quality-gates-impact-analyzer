package com.thesis.qualitygateanalyzer.repository.commit;

import com.thesis.qualitygateanalyzer.entity.commit.CommitHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommitHistoryRepository extends JpaRepository<CommitHistoryEntity, UUID> {

    List<CommitHistoryEntity> findByOwnerAndRepoOrderByCommitDateAsc(String owner, String repo);

    boolean existsByOwnerAndRepo(String owner, String repo);

    void deleteByOwnerAndRepo(String owner, String repo);
}
