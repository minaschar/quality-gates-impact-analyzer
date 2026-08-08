-- V3__commit_history.sql
-- Stores the full commit history per repository for deterministic chunking.
-- Independent of t_repositories: commit history can be fetched without running a full analysis.

CREATE TABLE t_commit_history
(
    id          UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    owner       VARCHAR(255)             NOT NULL,
    repo        VARCHAR(255)             NOT NULL,
    commit_sha  VARCHAR(40)              NOT NULL,
    commit_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_commit_history_owner_repo ON t_commit_history (owner, repo);
CREATE INDEX idx_commit_history_date ON t_commit_history (owner, repo, commit_date);
