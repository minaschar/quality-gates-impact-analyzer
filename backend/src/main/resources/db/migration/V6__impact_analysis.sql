-- V6__impact_analysis.sql
-- E2E Quality Impact Analysis: determines whether code quality (from the ingested
-- SonarQube metrics dataset) improved after each detected quality gate tool was introduced.
--
-- Independent of t_repositories (mirrors t_commit_history / t_quality_metric_snapshots):
-- keyed by (owner, repo) directly rather than a FK to a specific (versioned) detection row,
-- so a later re-detection never orphans an analysis. Only ever contains rows for repositories
-- that have a detected quality gate -- "no QG found" is never persisted.
--
-- t_impact_metrics_snapshot self-contained-copies the handful of metric columns it needs
-- from t_quality_metric_snapshots rather than FK-referencing it, because
-- QualityMetricsIngestionServiceImpl deletes and re-inserts (with new ids) that table's rows
-- on its own forceNewAnalysis, independent of this feature; an FK there would silently
-- cascade-delete impact analysis data.

CREATE TABLE t_impact_analysis
(
    id                      UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    owner                   VARCHAR(255) NOT NULL,
    repo                    VARCHAR(255) NOT NULL,
    repository_url          VARCHAR(500),

    tools_analyzed_count    INTEGER                  DEFAULT 0,
    overall_improvement_score DOUBLE PRECISION,
    overall_trend           VARCHAR(20)  NOT NULL, -- IMPROVED / DEGRADED / UNCHANGED / INSUFFICIENT_DATA

    total_snapshots_before  INTEGER                  DEFAULT 0,
    total_snapshots_after   INTEGER                  DEFAULT 0,
    date_range_start        TIMESTAMP WITH TIME ZONE,
    date_range_end          TIMESTAMP WITH TIME ZONE,

    analysis_time_ms        BIGINT,
    api_calls_made          INTEGER,
    computed_at              TIMESTAMP WITH TIME ZONE,

    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_impact_analysis_owner_repo UNIQUE (owner, repo)
);

CREATE INDEX idx_impact_analysis_owner_repo ON t_impact_analysis (owner, repo);
CREATE INDEX idx_impact_analysis_computed_at ON t_impact_analysis (computed_at);

CREATE TRIGGER update_impact_analysis_updated_at
    BEFORE UPDATE
    ON t_impact_analysis
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Per-commit metrics classified relative to a tool's introduction date. A single source
-- commit can appear once per relevant tool (BEFORE tool A, AFTER tool B), since tools are
-- typically introduced at different times.

CREATE TABLE t_impact_metrics_snapshot
(
    id                                       UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    impact_analysis_id                       UUID        NOT NULL REFERENCES t_impact_analysis (id) ON DELETE CASCADE,

    tool                                     VARCHAR(50) NOT NULL,
    classification                           VARCHAR(10) NOT NULL, -- BEFORE / AFTER

    pull_request_number                      INTEGER,
    analysis_role                            VARCHAR(20), -- pr_base / pr_closed
    commit_sha                               VARCHAR(40),
    commit_date                              TIMESTAMP WITH TIME ZONE NOT NULL,
    date_source                              VARCHAR(30), -- commit_history / pr_metadata

    bugs                                     INTEGER,
    vulnerabilities                          INTEGER,
    code_smells                              INTEGER,
    security_hotspots                        INTEGER,
    coverage                                 DOUBLE PRECISION,
    duplicated_lines_density                 DOUBLE PRECISION,
    ncloc                                    INTEGER,
    complexity                               INTEGER,
    cognitive_complexity                     INTEGER,
    software_quality_reliability_issues      INTEGER,
    software_quality_maintainability_issues  INTEGER,
    software_quality_security_issues         INTEGER,

    created_at                               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_impact_snapshot_analysis ON t_impact_metrics_snapshot (impact_analysis_id);
CREATE INDEX idx_impact_snapshot_tool ON t_impact_metrics_snapshot (impact_analysis_id, tool);
CREATE INDEX idx_impact_snapshot_tool_class ON t_impact_metrics_snapshot (impact_analysis_id, tool, classification);
CREATE INDEX idx_impact_snapshot_commit_date ON t_impact_metrics_snapshot (commit_date);

-- Aggregated before/after comparison, one row per quality gate tool.

CREATE TABLE t_impact_comparison
(
    id                    UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    impact_analysis_id    UUID        NOT NULL REFERENCES t_impact_analysis (id) ON DELETE CASCADE,

    tool                  VARCHAR(50) NOT NULL,
    tool_category         VARCHAR(50),
    introduced_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    introduced_commit_sha VARCHAR(40),

    samples_before        INTEGER                  DEFAULT 0,
    samples_after         INTEGER                  DEFAULT 0,

    improvement_score     DOUBLE PRECISION,
    trend                 VARCHAR(20) NOT NULL, -- IMPROVED / DEGRADED / UNCHANGED / INSUFFICIENT_DATA

    -- Per-metric {avgBefore, avgAfter, medianBefore, medianAfter, deltaAbsolute, deltaPercent, trend},
    -- keyed by metric name (bugs, vulnerabilities, codeSmells, coverage, ...). JSONB rather than a wide
    -- fixed-column table (avoids ~54 columns) and rather than the EAV shape t_quality_metrics used before
    -- it was dropped in V5 for being awkward to aggregate for UI consumption.
    metrics_comparison    JSONB       NOT NULL,

    created_at            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_impact_comparison_analysis_tool UNIQUE (impact_analysis_id, tool)
);

CREATE INDEX idx_impact_comparison_analysis ON t_impact_comparison (impact_analysis_id);
CREATE INDEX idx_impact_comparison_tool ON t_impact_comparison (tool);
CREATE INDEX idx_impact_comparison_trend ON t_impact_comparison (trend);
