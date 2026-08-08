-- V5__drop_quality_metrics.sql
-- Drops t_quality_metrics: the original "before/after" placeholder table from V1.
-- It was never wired to any entity relationship or repository call site and has
-- since been superseded by t_quality_metric_snapshots (see V4).

DROP TABLE IF EXISTS t_quality_metrics;
