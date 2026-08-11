package com.thesis.qualitygateanalyzer.controller.v1.impactanalysis;

import com.thesis.qualitygateanalyzer.controller.v1.ApiV1Controller;
import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.dto.response.ImpactAnalysisResponse;
import com.thesis.qualitygateanalyzer.exception.RepositoryDataNotFoundException;
import com.thesis.qualitygateanalyzer.exception.RepositoryNotFoundException;
import com.thesis.qualitygateanalyzer.service.impactanalysis.ImpactAnalysisService;
import com.thesis.qualitygateanalyzer.util.GitHubIdentifiers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The "force absolutely everything" endpoints for a repository -- run a fully forced,
 * end-to-end (E2E) analysis in one call, or delete every trace of one in one call.
 * <p>
 * A separate class from {@link ImpactAnalysisController} (its narrower {@code forceNewAnalysis}
 * flag intentionally never forces detection or commits, and its own delete only removes the
 * detection aggregate -- see that class's own docs), but the same package: this controller
 * depends on nothing but {@link ImpactAnalysisService}, exactly like {@link ImpactAnalysisController}
 * does, so it's really just a second, fully-forced front door onto the same service rather than
 * a distinct feature area of its own.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "E2E Analysis", description = "Force a complete end-to-end analysis: detection, commit history, quality metrics, and the before/after comparison, all in one call")
@CrossOrigin(origins = "*")
public class E2EAnalysisController implements ApiV1Controller {

    private final ImpactAnalysisService impactAnalysisService;

    /**
     * Forces a fresh detection, commit history fetch, and quality-metrics re-ingestion via
     * {@link ImpactAnalysisService#refreshRepositoryData}, then immediately recomputes the
     * before/after comparison from that refreshed data via {@link ImpactAnalysisService#analyze}
     * with {@code forceNewAnalysis=true}. Deliberately two separate service calls (not merged
     * into one transactional method) so each keeps its own transaction boundary: if the
     * comparison step fails, the already-committed detection/commit/metrics refresh isn't lost
     * and doesn't need to be redone.
     */
    @PostMapping("/e2e-analysis")
    @Operation(
            summary = "Run E2E Analysis",
            description = "Forces a fresh quality-gate detection, commit history fetch, quality-metrics " +
                    "re-ingestion, and -- if a quality gate is found -- an immediate recomputation of the " +
                    "before/after comparison from that refreshed data. The single call for a fully forced, " +
                    "end-to-end result -- no follow-up call needed."
    )
    public ResponseEntity<ApiResponse<ImpactAnalysisResponse>> runE2EAnalysis(
            @Parameter(description = "Repository owner") @RequestParam String owner,
            @Parameter(description = "Repository name") @RequestParam String repo) {

        owner = GitHubIdentifiers.normalize(owner);
        repo = GitHubIdentifiers.normalize(repo);

        log.info("E2E analysis request for {}/{}", owner, repo);

        try {
            impactAnalysisService.refreshRepositoryData(owner, repo);
            ImpactAnalysisResponse result = impactAnalysisService.analyze(owner, repo, true);

            String message = result.isHasQualityGate()
                    ? "E2E analysis complete for " + owner + "/" + repo
                    : "E2E analysis complete; no quality gate detected for " + owner + "/" + repo;

            return ResponseEntity.ok(ApiResponse.<ImpactAnalysisResponse>builder()
                    .success(true)
                    .message(message)
                    .data(result)
                    .build());

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ImpactAnalysisResponse>builder()
                            .success(false)
                            .error(e.getMessage())
                            .build());

        } catch (RepositoryNotFoundException e) {
            // Let the global exception handler build the response (same 400 contract as above).
            throw e;

        } catch (Exception e) {
            log.error("E2E analysis failed for {}/{}", owner, repo, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<ImpactAnalysisResponse>builder()
                            .success(false)
                            .error("E2E analysis failed: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Deletes every stored trace of a repository via {@link ImpactAnalysisService#deleteAllRepositoryData}.
     * Unlike {@code DELETE /quality-gate/{owner}/{repo}}, which only removes detection data,
     * this leaves nothing behind in any table -- detection, commit history, quality metrics,
     * and impact analysis are all removed.
     */
    @DeleteMapping("/e2e-analysis")
    @Operation(
            summary = "Delete All Repository Data",
            description = "Deletes every stored trace of a repository -- quality-gate detection, commit " +
                    "history, quality metrics, and any computed impact analysis. Unlike DELETE " +
                    "/quality-gate/{owner}/{repo}, which only removes detection data, this leaves nothing " +
                    "behind in any table. Returns 404 if no data exists for this repository at all."
    )
    public ResponseEntity<ApiResponse<Void>> deleteAllRepositoryData(
            @Parameter(description = "Repository owner") @RequestParam String owner,
            @Parameter(description = "Repository name") @RequestParam String repo) {

        owner = GitHubIdentifiers.normalize(owner);
        repo = GitHubIdentifiers.normalize(repo);

        log.info("Delete all data request for {}/{}", owner, repo);

        try {
            impactAnalysisService.deleteAllRepositoryData(owner, repo);

            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .success(true)
                    .message("All data deleted for " + owner + "/" + repo)
                    .build());

        } catch (RepositoryDataNotFoundException e) {
            // Let the global exception handler build the 404 response.
            throw e;

        } catch (Exception e) {
            log.error("Delete all data failed for {}/{}", owner, repo, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .error("Delete all data failed: " + e.getMessage())
                            .build());
        }
    }
}
