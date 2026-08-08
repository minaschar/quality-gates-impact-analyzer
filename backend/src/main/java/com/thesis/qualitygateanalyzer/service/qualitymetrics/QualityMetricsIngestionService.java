package com.thesis.qualitygateanalyzer.service.qualitymetrics;

import com.thesis.qualitygateanalyzer.dto.response.BulkIngestSummary;
import com.thesis.qualitygateanalyzer.dto.response.QualityMetricsResponse;

/**
 * Ingests per-commit software quality metrics from the source dataset into the database,
 * and serves previously ingested data. Follows the same cache semantics as /analyze:
 * forceNewAnalysis=false with existing data returns the cached rows; otherwise a fresh
 * ingestion is performed (and persisted) from the dataset.
 */
public interface QualityMetricsIngestionService {

    record IngestResult(QualityMetricsResponse response, boolean fromCache) {
    }

    /**
     * Ingest software quality metrics for a repository from the source dataset.
     */
    IngestResult ingest(String owner, String repo, boolean forceNewAnalysis);

    /**
     * Get previously ingested quality metrics for a repository.
     */
    QualityMetricsResponse getStored(String owner, String repo);

    /**
     * Check whether a repository already has ingested quality metrics.
     */
    boolean hasStoredMetrics(String owner, String repo);

    /**
     * Ingests every repository present in the source dataset in a single pass.
     * Per-repository caching still applies: a repository already ingested is skipped
     * unless forceNewAnalysis=true.
     */
    BulkIngestSummary bulkIngest(boolean forceNewAnalysis);
}
