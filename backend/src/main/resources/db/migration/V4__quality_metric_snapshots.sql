-- V4__quality_metric_snapshots.sql
-- Stores per-commit SonarQube software quality metrics ingested from the source dataset.
-- Independent of t_repositories: metrics can be ingested without running a full /analyze first,
-- mirroring the t_commit_history design.

CREATE TABLE t_quality_metric_snapshots
(
    id                                      UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    owner                                   VARCHAR(255)             NOT NULL,
    repo                                    VARCHAR(255)             NOT NULL,
    repository_url                          VARCHAR(500),

    pull_request_number                     INTEGER,
    pull_request_title                      TEXT,
    pull_request_url                        VARCHAR(500),
    pull_request_state                      VARCHAR(50),
    pull_request_merged                     BOOLEAN,
    base_branch_name                        VARCHAR(255),
    head_branch_name                        VARCHAR(255),

    analysis_role                           VARCHAR(50)              NOT NULL,
    commit_hash                             VARCHAR(40),
    first_pull_request_commit_hash          VARCHAR(40),
    closing_commit_hash                     VARCHAR(40),

    sonar_project_key                       VARCHAR(500),
    sonar_project_name                      VARCHAR(500),
    sonar_dashboard_url                     VARCHAR(1000),
    sonar_task_id                           VARCHAR(100),
    sonar_analysis_id                       VARCHAR(100),
    quality_gate_status                     VARCHAR(20),

    bugs                                    INTEGER,
    vulnerabilities                         INTEGER,
    code_smells                             INTEGER,
    security_hotspots                       INTEGER,
    coverage                                DOUBLE PRECISION,
    duplicated_lines_density                DOUBLE PRECISION,
    ncloc                                   INTEGER,
    complexity                              INTEGER,
    cognitive_complexity                    INTEGER,
    software_quality_reliability_issues     INTEGER,
    software_quality_maintainability_issues INTEGER,
    software_quality_security_issues        INTEGER,

    issues_summary                          JSONB,
    categories                              VARCHAR(500),

    source_row_id                           BIGINT,
    source_created_at                       TIMESTAMP WITH TIME ZONE,
    source_updated_at                       TIMESTAMP WITH TIME ZONE,

    created_at                              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_quality_metric_snapshots_source_row UNIQUE (source_row_id)
);

CREATE INDEX idx_qms_owner_repo ON t_quality_metric_snapshots (owner, repo);
CREATE INDEX idx_qms_owner_repo_commit ON t_quality_metric_snapshots (owner, repo, commit_hash);
CREATE INDEX idx_qms_owner_repo_pr ON t_quality_metric_snapshots (owner, repo, pull_request_number);
CREATE INDEX idx_qms_role ON t_quality_metric_snapshots (analysis_role);
