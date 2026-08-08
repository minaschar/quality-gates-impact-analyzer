-- V1__initial_schema.sql
-- Quality Gate Analyzer - Initial Database Schema

-- Configuration table: stores runtime configuration values (editable from UI)

CREATE TABLE t_configuration
(
    id           UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    config_key   VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT         NOT NULL,
    data_type    VARCHAR(50)  NOT NULL    DEFAULT 'STRING',
    category     VARCHAR(50)  NOT NULL,
    description  TEXT,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_configuration_key ON t_configuration (config_key);
CREATE INDEX idx_configuration_category ON t_configuration (category);

-- Repositories table: main table storing analyzed repositories

CREATE TABLE t_repositories
(
    id                     UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    owner                  VARCHAR(255) NOT NULL,
    repo                   VARCHAR(255) NOT NULL,
    url                    VARCHAR(500),
    description            TEXT,
    primary_language       VARCHAR(100),
    default_branch         VARCHAR(255),
    stars                  INTEGER                  DEFAULT 0,
    forks                  INTEGER                  DEFAULT 0,
    has_quality_gate       BOOLEAN                  DEFAULT FALSE,
    recommendation         VARCHAR(100),
    conclusion             TEXT,

    -- Analysis metadata
    analysis_version       INTEGER                  DEFAULT 1,
    is_current             BOOLEAN                  DEFAULT TRUE,
    analysis_time_ms       BIGINT,
    api_calls_made         INTEGER,
    analyzed_at            TIMESTAMP WITH TIME ZONE,

    -- First commit info (for history context)
    first_commit_sha       VARCHAR(40),
    first_commit_date      TIMESTAMP WITH TIME ZONE,
    total_commits          INTEGER,

    -- Branch protection (stored as JSONB for flexibility)
    branch_protection      JSONB,
    required_qg_tools      JSONB,
    informational_qg_tools JSONB,

    -- Timestamps
    created_at             TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_repositories_owner_repo_version UNIQUE (owner, repo, analysis_version)
);

CREATE INDEX idx_repositories_owner_repo ON t_repositories (owner, repo);
CREATE INDEX idx_repositories_is_current ON t_repositories (is_current);
CREATE INDEX idx_repositories_analyzed_at ON t_repositories (analyzed_at);
CREATE INDEX idx_repositories_has_qg ON t_repositories (has_quality_gate);

-- QG detections table: Phase 1 detected quality gate tools from static analysis

CREATE TABLE t_qg_detections
(
    id                  UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    repository_id       UUID        NOT NULL REFERENCES t_repositories (id) ON DELETE CASCADE,
    tool                VARCHAR(50) NOT NULL,
    category            VARCHAR(50) NOT NULL,
    source_file         VARCHAR(500),
    source_type         VARCHAR(50) NOT NULL,
    evidence_found      JSONB,
    confidence_score    DOUBLE PRECISION         DEFAULT 1.0,
    triggers_on_pr      BOOLEAN,
    associated_workflow VARCHAR(500),
    relevant_for_thesis BOOLEAN                  DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_qg_detections_repository ON t_qg_detections (repository_id);
CREATE INDEX idx_qg_detections_tool ON t_qg_detections (tool);
CREATE INDEX idx_qg_detections_category ON t_qg_detections (category);

-- QG workflows table: workflows that contain quality gate tools

CREATE TABLE t_qg_workflows
(
    id             UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    repository_id  UUID         NOT NULL REFERENCES t_repositories (id) ON DELETE CASCADE,
    workflow_file  VARCHAR(500) NOT NULL,
    workflow_name  VARCHAR(255),
    tools          JSONB,
    triggers_on_pr BOOLEAN                  DEFAULT FALSE,
    build_commands JSONB,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_qg_workflows_repository ON t_qg_workflows (repository_id);

-- Tool introductions table: when each QG tool was introduced

CREATE TABLE t_tool_introductions
(
    id                     UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    repository_id          UUID        NOT NULL REFERENCES t_repositories (id) ON DELETE CASCADE,
    tool                   VARCHAR(50) NOT NULL,
    category               VARCHAR(50) NOT NULL,

    -- Effective introduction (computed)
    effective_sha          VARCHAR(40),
    effective_short_sha    VARCHAR(7),
    effective_date         TIMESTAMP WITH TIME ZONE,
    author                 VARCHAR(255),
    commit_message         TEXT,

    -- Detection method
    detection_method       VARCHAR(100),
    file_path              VARCHAR(500),
    search_pattern         VARCHAR(500),

    -- Status flags
    present_since_creation BOOLEAN                  DEFAULT FALSE,
    introduction_summary   TEXT,

    created_at             TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tool_introductions_repository ON t_tool_introductions (repository_id);
CREATE INDEX idx_tool_introductions_tool ON t_tool_introductions (tool);
CREATE INDEX idx_tool_introductions_date ON t_tool_introductions (effective_date);

-- File introductions table: detailed file-level introduction history for each tool

CREATE TABLE t_file_introductions
(
    id                          UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    tool_introduction_id        UUID         NOT NULL REFERENCES t_tool_introductions (id) ON DELETE CASCADE,
    introduction_type           VARCHAR(50)  NOT NULL, -- 'CONFIG' or 'CI'
    file_path                   VARCHAR(500) NOT NULL,
    search_pattern              VARCHAR(500),

    -- Introduction commit
    introduced_sha              VARCHAR(40),
    introduced_short_sha        VARCHAR(7),
    introduced_date             TIMESTAMP WITH TIME ZONE,
    introduced_author           VARCHAR(255),
    introduced_message          TEXT,

    present_since_file_creation BOOLEAN                  DEFAULT FALSE,
    all_occurrences             JSONB,

    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_file_introductions_tool ON t_file_introductions (tool_introduction_id);

-- Enforcement table: enforcement analysis results

CREATE TABLE t_enforcement
(
    id                     UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    repository_id          UUID        NOT NULL REFERENCES t_repositories (id) ON DELETE CASCADE,

    -- Main metrics
    status                 VARCHAR(50) NOT NULL,
    score                  DOUBLE PRECISION,
    confidence             VARCHAR(20), -- HIGH, MEDIUM, LOW

    -- PR statistics
    total_prs_analyzed     INTEGER                  DEFAULT 0,
    prs_with_qg_failures   INTEGER                  DEFAULT 0,
    fixed_then_merged      INTEGER                  DEFAULT 0,
    blocked                INTEGER                  DEFAULT 0,
    merged_with_failure    INTEGER                  DEFAULT 0,
    still_open             INTEGER                  DEFAULT 0,

    -- Branch protection context (JSONB for flexibility)
    branch_protection_info JSONB,

    -- Fallback info if no failures found
    fallback_info          JSONB,

    interpretation         TEXT,

    created_at             TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_enforcement_repository UNIQUE (repository_id)
);

CREATE INDEX idx_enforcement_repository ON t_enforcement (repository_id);
CREATE INDEX idx_enforcement_status ON t_enforcement (status);

-- Enforcement by tool table: per-tool enforcement breakdown

CREATE TABLE t_enforcement_by_tool
(
    id                UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    enforcement_id    UUID        NOT NULL REFERENCES t_enforcement (id) ON DELETE CASCADE,
    tool              VARCHAR(50) NOT NULL,
    failures          INTEGER                  DEFAULT 0,
    enforced          INTEGER                  DEFAULT 0,
    bypassed          INTEGER                  DEFAULT 0,
    is_required_check BOOLEAN                  DEFAULT FALSE,
    enforcement_rate  DOUBLE PRECISION,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_enforcement_by_tool_enforcement ON t_enforcement_by_tool (enforcement_id);
CREATE INDEX idx_enforcement_by_tool_tool ON t_enforcement_by_tool (tool);

-- PR samples table: sample PRs analyzed for enforcement

CREATE TABLE t_pr_samples
(
    id                      UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    enforcement_id          UUID    NOT NULL REFERENCES t_enforcement (id) ON DELETE CASCADE,

    -- PR info
    pr_number               INTEGER NOT NULL,
    pr_title                TEXT,
    pr_url                  VARCHAR(500),
    state                   VARCHAR(50),
    merged                  BOOLEAN                  DEFAULT FALSE,

    -- Timestamps
    pr_created_at           TIMESTAMP WITH TIME ZONE,
    pr_closed_at            TIMESTAMP WITH TIME ZONE,
    pr_merged_at            TIMESTAMP WITH TIME ZONE,

    -- Workflow analysis
    had_workflow_failure    BOOLEAN                  DEFAULT FALSE,
    last_workflow_passed    BOOLEAN,

    -- QG check analysis
    had_verified_qg_failure BOOLEAN                  DEFAULT FALSE,
    last_qg_check_passed    BOOLEAN,
    failed_qg_tools         JSONB,
    failure_messages        JSONB,

    -- Outcome
    qg_was_required_check   BOOLEAN                  DEFAULT FALSE,
    outcome                 VARCHAR(50),
    outcome_reason          TEXT,

    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pr_samples_enforcement ON t_pr_samples (enforcement_id);
CREATE INDEX idx_pr_samples_outcome ON t_pr_samples (outcome);
CREATE INDEX idx_pr_samples_pr_number ON t_pr_samples (pr_number);

-- PR workflow runs table: workflow runs associated with each PR sample

CREATE TABLE t_pr_workflow_runs
(
    id             UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    pr_sample_id   UUID   NOT NULL REFERENCES t_pr_samples (id) ON DELETE CASCADE,

    run_id         BIGINT NOT NULL,
    workflow_file  VARCHAR(500),
    workflow_name  VARCHAR(255),
    head_sha       VARCHAR(40),
    conclusion     VARCHAR(50),
    status         VARCHAR(50),
    event          VARCHAR(50),
    html_url       VARCHAR(500),
    run_created_at TIMESTAMP WITH TIME ZONE,

    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pr_workflow_runs_pr ON t_pr_workflow_runs (pr_sample_id);

-- PR check runs table: check runs associated with each PR sample

CREATE TABLE t_pr_check_runs
(
    id               UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    pr_sample_id     UUID   NOT NULL REFERENCES t_pr_samples (id) ON DELETE CASCADE,

    check_run_id     BIGINT NOT NULL,
    name             VARCHAR(500),
    app_slug         VARCHAR(255),
    conclusion       VARCHAR(50),
    status           VARCHAR(50),
    output_title     TEXT,
    output_summary   TEXT,
    head_sha         VARCHAR(40),
    completed_at     TIMESTAMP WITH TIME ZONE,

    -- Matched tool info
    matched_tool     VARCHAR(50),
    match_confidence DOUBLE PRECISION,

    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pr_check_runs_pr ON t_pr_check_runs (pr_sample_id);
CREATE INDEX idx_pr_check_runs_matched_tool ON t_pr_check_runs (matched_tool);

-- PR commit statuses table: commit statuses (external apps) associated with each PR sample

CREATE TABLE t_pr_commit_statuses
(
    id                UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    pr_sample_id      UUID NOT NULL REFERENCES t_pr_samples (id) ON DELETE CASCADE,

    status_id         BIGINT,
    state             VARCHAR(50),
    context           VARCHAR(500),
    description       TEXT,
    target_url        VARCHAR(1000),
    status_created_at TIMESTAMP WITH TIME ZONE,
    status_updated_at TIMESTAMP WITH TIME ZONE,

    -- Matched tool info
    matched_tool      VARCHAR(50),
    match_confidence  DOUBLE PRECISION,

    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pr_commit_statuses_pr ON t_pr_commit_statuses (pr_sample_id);
CREATE INDEX idx_pr_commit_statuses_matched_tool ON t_pr_commit_statuses (matched_tool);

-- Quality metrics table: before/after quality comparison metrics

CREATE TABLE t_quality_metrics
(
    id                   UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    repository_id        UUID         NOT NULL REFERENCES t_repositories (id) ON DELETE CASCADE,
    tool_introduction_id UUID         REFERENCES t_tool_introductions (id) ON DELETE SET NULL,

    measurement_point    VARCHAR(20)  NOT NULL, -- 'BEFORE' or 'AFTER'
    commit_sha           VARCHAR(40),
    commit_date          TIMESTAMP WITH TIME ZONE,

    metric_type          VARCHAR(100) NOT NULL,
    metric_value         DOUBLE PRECISION,
    metric_details       JSONB,

    measured_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at           TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_quality_metrics_repository ON t_quality_metrics (repository_id);
CREATE INDEX idx_quality_metrics_tool ON t_quality_metrics (tool_introduction_id);
CREATE INDEX idx_quality_metrics_point ON t_quality_metrics (measurement_point);

-- History analysis metadata table: stores metadata about the history analysis process

CREATE TABLE t_history_analysis_metadata
(
    id                   UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    repository_id        UUID NOT NULL REFERENCES t_repositories (id) ON DELETE CASCADE,

    method               VARCHAR(100),
    files_analyzed       INTEGER                  DEFAULT 0,
    tools_analyzed       INTEGER                  DEFAULT 0,
    analysis_duration_ms BIGINT,
    success              BOOLEAN                  DEFAULT TRUE,
    error_message        TEXT,

    created_at           TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_history_metadata_repository UNIQUE (repository_id)
);

CREATE INDEX idx_history_metadata_repository ON t_history_analysis_metadata (repository_id);

-- Auto-update updated_at timestamp trigger

CREATE
OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at
= CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$
language 'plpgsql';

CREATE TRIGGER update_configuration_updated_at
    BEFORE UPDATE
    ON t_configuration
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_repositories_updated_at
    BEFORE UPDATE
    ON t_repositories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
