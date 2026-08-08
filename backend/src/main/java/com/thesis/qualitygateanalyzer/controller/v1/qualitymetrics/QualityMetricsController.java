package com.thesis.qualitygateanalyzer.controller.v1.qualitymetrics;

import com.thesis.qualitygateanalyzer.controller.v1.ApiV1Controller;
import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.dto.response.BulkIngestSummary;
import com.thesis.qualitygateanalyzer.dto.response.QualityMetricsResponse;
import com.thesis.qualitygateanalyzer.exception.NoDataInDatasetException;
import com.thesis.qualitygateanalyzer.exception.QualityMetricsNotFoundException;
import com.thesis.qualitygateanalyzer.service.qualitymetrics.QualityMetricsIngestionService;
import com.thesis.qualitygateanalyzer.service.qualitymetrics.QualityMetricsIngestionService.IngestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for ingesting and retrieving per-commit software quality metrics.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Software Quality Metrics", description = "Ingest and retrieve per-commit SonarQube quality metrics for a repository")
@CrossOrigin(origins = "*")
public class QualityMetricsController implements ApiV1Controller {

    private final QualityMetricsIngestionService ingestionService;

    /**
     * Ingest software quality metrics for a repository from the source dataset.
     * Results are cached — subsequent calls return cached data unless forceNewAnalysis=true.
     */
    @PostMapping("/repositories/{organization}/{repo}/quality-metrics")
    @Operation(
            summary = "Ingest Software Quality Metrics",
            description = "Stores SonarQube quality metrics for every analyzed commit (PR base and PR closed) " +
                    "of a repository from the source dataset. Results are cached in the database. " +
                    "Use forceNewAnalysis=true to bypass the cache and re-ingest."
    )
    public ResponseEntity<ApiResponse<QualityMetricsResponse>> ingest(
            @PathVariable String organization,
            @PathVariable String repo,
            @Parameter(description = "Force a new ingestion even if cached data exists")
            @RequestParam(defaultValue = "false") boolean forceNewAnalysis) {

        log.info("Quality metrics ingest request for {}/{} (forceNewAnalysis={})",
                organization, repo, forceNewAnalysis);

        try {
            IngestResult result = ingestionService.ingest(organization, repo, forceNewAnalysis);

            return ResponseEntity.ok(ApiResponse.<QualityMetricsResponse>builder()
                    .success(true)
                    .message(result.fromCache()
                            ? "Quality metrics retrieved from cache"
                            : "Quality metrics ingested and saved")
                    .data(result.response())
                    .build());

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.<QualityMetricsResponse>builder()
                    .success(false)
                    .error(e.getMessage())
                    .build());

        } catch (NoDataInDatasetException e) {
            // Let the global exception handler build the response (same 404 contract as before).
            throw e;

        } catch (Exception e) {
            log.error("Quality metrics ingestion failed for {}/{}", organization, repo, e);
            return ResponseEntity.internalServerError().body(ApiResponse.<QualityMetricsResponse>builder()
                    .success(false)
                    .error("Quality metrics ingestion failed: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Bulk ingest software quality metrics for every repository in the source dataset.
     */
    @PostMapping("/quality-metrics/bulk-ingest")
    @Operation(
            summary = "Bulk Ingest Software Quality Metrics",
            description = "Reads the entire software quality metrics dataset in a single pass and stores metrics " +
                    "for every repository it contains. Per-repository caching still applies: a repository that " +
                    "already has stored data is skipped unless forceNewAnalysis=true."
    )
    public ResponseEntity<ApiResponse<BulkIngestSummary>> bulkIngest(
            @Parameter(description = "Force re-ingestion for repositories that already have stored data")
            @RequestParam(defaultValue = "false") boolean forceNewAnalysis) {

        log.info("Bulk quality metrics ingest request (forceNewAnalysis={})", forceNewAnalysis);

        try {
            BulkIngestSummary summary = ingestionService.bulkIngest(forceNewAnalysis);

            return ResponseEntity.ok(ApiResponse.<BulkIngestSummary>builder()
                    .success(true)
                    .message("Bulk ingestion completed: " + summary.getRepositoriesIngested() + " of " +
                            summary.getTotalRepositoriesInDataset() + " repositories ingested (" +
                            summary.getRepositoriesFromCache() + " served from cache)")
                    .data(summary)
                    .build());

        } catch (Exception e) {
            log.error("Bulk quality metrics ingestion failed", e);
            return ResponseEntity.internalServerError().body(ApiResponse.<BulkIngestSummary>builder()
                    .success(false)
                    .error("Bulk ingestion failed: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Get cached software quality metrics for a repository (without re-ingesting).
     */
    @GetMapping("/repositories/{organization}/{repo}/quality-metrics")
    @Operation(
            summary = "Get Stored Software Quality Metrics",
            description = "Retrieve previously ingested software quality metrics for a repository. " +
                    "Returns 404 if none have been ingested yet."
    )
    public ResponseEntity<ApiResponse<QualityMetricsResponse>> getStored(
            @PathVariable String organization,
            @PathVariable String repo) {

        log.info("Get quality metrics request for {}/{}", organization, repo);

        if (!ingestionService.hasStoredMetrics(organization, repo)) {
            throw new QualityMetricsNotFoundException("No quality metrics found for " + organization + "/" + repo);
        }

        QualityMetricsResponse response = ingestionService.getStored(organization, repo);

        return ResponseEntity.ok(ApiResponse.<QualityMetricsResponse>builder()
                .success(true)
                .message("Quality metrics retrieved")
                .data(response)
                .build());
    }
}
