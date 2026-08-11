package com.thesis.qualitygateanalyzer.controller.v1.e2eanalysis;

import com.thesis.qualitygateanalyzer.controller.v1.ApiV1Controller;
import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.dto.response.ImpactAnalysisResponse;
import com.thesis.qualitygateanalyzer.exception.RepositoryNotFoundException;
import com.thesis.qualitygateanalyzer.service.impactanalysis.ImpactAnalysisService;
import com.thesis.qualitygateanalyzer.util.GitHubIdentifiers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single "force absolutely everything" action for a repository: a fresh quality-gate
 * detection, commit history fetch, quality-metrics re-ingestion, and (if a quality gate is
 * found) an immediate recomputation of the before/after comparison from that refreshed data --
 * a genuinely end-to-end (E2E) forced analysis in one call.
 * <p>
 * Deliberately a separate controller from {@code ImpactAnalysisController} and
 * {@code QualityGateDetectionController}: this endpoint's whole purpose is to force every step
 * both of those controllers can individually force on their own (narrower) terms, so grouping
 * it under either one would misrepresent its scope.
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
}
