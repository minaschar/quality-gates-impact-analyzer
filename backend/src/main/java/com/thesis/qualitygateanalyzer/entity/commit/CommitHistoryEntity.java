package com.thesis.qualitygateanalyzer.entity.commit;

import com.thesis.qualitygateanalyzer.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

/**
 * One row per commit in a repository's default-branch history.
 * Rows are inserted once and reused across chunk requests (cache-first strategy).
 */
@Entity
@Table(name = "t_commit_history", indexes = {
        @Index(name = "idx_commit_history_owner_repo", columnList = "owner, repo"),
        @Index(name = "idx_commit_history_date", columnList = "owner, repo, commit_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitHistoryEntity extends BaseEntity {

    @Column(name = "owner", nullable = false)
    private String owner;

    @Column(name = "repo", nullable = false)
    private String repo;

    @Column(name = "commit_sha", length = 40, nullable = false)
    private String commitSha;

    @Column(name = "commit_date", nullable = false)
    private Instant commitDate;
}
