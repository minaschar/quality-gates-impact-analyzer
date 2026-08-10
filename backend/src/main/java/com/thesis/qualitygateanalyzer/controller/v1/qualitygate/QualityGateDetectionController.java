package com.thesis.qualitygateanalyzer.controller.v1.qualitygate;

import com.thesis.qualitygateanalyzer.controller.v1.ApiV1Controller;
import com.thesis.qualitygateanalyzer.domain.qualitygate.RepositoryDetectionResult;
import com.thesis.qualitygateanalyzer.dto.request.QualityGateDetectionRequest;
import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.exception.QualityGateDetectionNotFoundException;
import com.thesis.qualitygateanalyzer.exception.RepositoryNotFoundException;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import com.thesis.qualitygateanalyzer.service.qualitygate.PersistenceService;
import com.thesis.qualitygateanalyzer.service.qualitygate.QualityGateDetectionService;
import com.thesis.qualitygateanalyzer.util.GitHubIdentifiers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REST API for quality gate detection.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Quality Gate Detection", description = "Detect quality gate configurations and enforcement in repositories")
@CrossOrigin(origins = "*")
public class QualityGateDetectionController implements ApiV1Controller {

    private final QualityGateDetectionService detectionService;
    private final GitHubApiClient githubClient;
    private final PersistenceService persistenceService;

    private static final Pattern GITHUB_URL = Pattern.compile(
            "(?:https?://)?(?:www\\.)?github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$"
    );

    // QUALITY GATE DETECTION ENDPOINTS

    /**
     * Detect quality gate configurations and enforcement for a repository.
     * Results are cached — subsequent calls return cached data unless forceNewDetection=true.
     */
    @PostMapping("/quality-gate/detect")
    @Operation(
            summary = "Detect Quality Gate",
            description = "Detects quality gates, verifies they run on PRs, and checks whether they " +
                    "block merges when checks fail. Results are cached. Use forceNewDetection=true to bypass the cache."
    )
    public ResponseEntity<ApiResponse<RepositoryDetectionResult>> detectQualityGate(
            @RequestBody QualityGateDetectionRequest request,
            @Parameter(description = "Force a new detection even if cached data exists")
            @RequestParam(defaultValue = "false") boolean forceNewDetection) {

        log.info("Detection request for: {} (forceNewDetection={})", request.getRepositoryUrl(), forceNewDetection);

        try {
            String[] ownerRepo = parseUrl(request.getRepositoryUrl());
            String owner = GitHubIdentifiers.normalize(ownerRepo[0]);
            String repo = GitHubIdentifiers.normalize(ownerRepo[1]);
            String canonicalUrl = "https://github.com/" + owner + "/" + repo;

            // Check for cached detection (unless forcing new)
            if (!forceNewDetection) {
                Optional<RepositoryDetectionResult> cached = persistenceService.loadDetection(owner, repo);
                if (cached.isPresent()) {
                    log.info("Returning cached detection for {}/{}", owner, repo);
                    return ResponseEntity.ok(ApiResponse.<RepositoryDetectionResult>builder()
                            .success(true)
                            .message("Detection retrieved from cache")
                            .data(cached.get())
                            .build());
                }
            }

            // Run fresh detection
            log.info("Running fresh detection for {}/{}", owner, repo);
            RepositoryDetectionResult result = detectionService.detect(canonicalUrl);

            // Save to database
            persistenceService.saveDetection(result, forceNewDetection);

            return ResponseEntity.ok(ApiResponse.<RepositoryDetectionResult>builder()
                    .success(true)
                    .message(forceNewDetection ? "Detection completed and saved" : "Detection completed and cached")
                    .data(result)
                    .build());

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<RepositoryDetectionResult>builder()
                            .success(false)
                            .error(e.getMessage())
                            .build());

        } catch (RepositoryNotFoundException e) {
            // Let the global exception handler build the response (same 400 contract as above).
            throw e;

        } catch (Exception e) {
            log.error("Detection failed", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<RepositoryDetectionResult>builder()
                            .success(false)
                            .error("Detection failed: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Get cached quality gate detection result for a repository (without running a new detection).
     */
    @GetMapping("/quality-gate/{owner}/{repo}")
    @Operation(
            summary = "Get Cached Quality Gate Detection",
            description = "Retrieve cached detection for a repository. Returns 404 if not found."
    )
    public ResponseEntity<ApiResponse<RepositoryDetectionResult>> getQualityGateDetection(
            @PathVariable String owner,
            @PathVariable String repo) {

        String normalizedOwner = GitHubIdentifiers.normalize(owner);
        String normalizedRepo = GitHubIdentifiers.normalize(repo);

        log.info("Get detection request for {}/{}", normalizedOwner, normalizedRepo);

        RepositoryDetectionResult result = persistenceService.loadDetection(normalizedOwner, normalizedRepo)
                .orElseThrow(() -> new QualityGateDetectionNotFoundException("No detection found for " + normalizedOwner + "/" + normalizedRepo));

        return ResponseEntity.ok(ApiResponse.<RepositoryDetectionResult>builder()
                .success(true)
                .message("Detection retrieved")
                .data(result)
                .build());
    }

    /**
     * List all repositories with a quality gate detection result.
     */
    @GetMapping("/quality-gate")
    @Operation(
            summary = "List Quality Gate Detections",
            description = "Get a list of all repositories that have been detected."
    )
    public ResponseEntity<ApiResponse<List<RepositoryDetectionResult>>> listRepositories(
            @Parameter(description = "Filter by quality gate presence")
            @RequestParam(required = false) Boolean hasQualityGate) {

        log.info("List repositories request (hasQualityGate={})", hasQualityGate);

        List<RepositoryDetectionResult> results;
        if (hasQualityGate != null && hasQualityGate) {
            results = persistenceService.listWithQualityGate();
        } else {
            results = persistenceService.listAllDetections();
        }

        return ResponseEntity.ok(ApiResponse.<List<RepositoryDetectionResult>>builder()
                .success(true)
                .message("Found " + results.size() + " repositories")
                .data(results)
                .build());
    }

    /**
     * Delete cached quality gate detection result for a repository.
     */
    @DeleteMapping("/quality-gate/{owner}/{repo}")
    @Operation(
            summary = "Delete Quality Gate Detection",
            description = "Delete all cached detection data for a repository."
    )
    public ResponseEntity<ApiResponse<Void>> deleteQualityGateDetection(
            @PathVariable String owner,
            @PathVariable String repo) {

        owner = GitHubIdentifiers.normalize(owner);
        repo = GitHubIdentifiers.normalize(repo);

        log.info("Delete detection request for {}/{}", owner, repo);

        if (!persistenceService.hasDetection(owner, repo)) {
            throw new QualityGateDetectionNotFoundException("No detection found for " + owner + "/" + repo);
        }

        persistenceService.deleteDetection(owner, repo);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Detection deleted for " + owner + "/" + repo)
                .build());
    }

    // UTILITY ENDPOINTS

    /**
     * Check GitHub API rate limit.
     */
    @GetMapping("/rate-limit")
    @Operation(summary = "Check GitHub API rate limit")
    public ResponseEntity<Map<String, Object>> getRateLimit() {
        int remaining = githubClient.getRemainingRateLimit();
        return ResponseEntity.ok(Map.of(
                "remaining", remaining,
                "limit", 5000,
                "message", remaining > 100 ? "OK" : "Running low!"
        ));
    }

    /**
     * Health check.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "version", "1.0.0",
                "database", "PostgreSQL"
        ));
    }

    // HELPERS

    private String[] parseUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Repository URL is required");
        }

        Matcher matcher = GITHUB_URL.matcher(url.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid GitHub URL: " + url);
        }

        return new String[]{matcher.group(1), matcher.group(2)};
    }
}
