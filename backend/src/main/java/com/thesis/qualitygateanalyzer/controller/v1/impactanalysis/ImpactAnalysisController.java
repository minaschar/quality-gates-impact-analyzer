package com.thesis.qualitygateanalyzer.controller.v1.impactanalysis;

import com.thesis.qualitygateanalyzer.controller.v1.ApiV1Controller;
import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.dto.response.ImpactAnalysisResponse;
import com.thesis.qualitygateanalyzer.dto.response.ImpactAnalysisSummaryDto;
import com.thesis.qualitygateanalyzer.exception.RepositoryNotFoundException;
import com.thesis.qualitygateanalyzer.service.impactanalysis.ImpactAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for E2E quality impact analysis: whether a repository's code quality (from the
 * ingested SonarQube metrics dataset) improved after its quality gate tool(s) were introduced.
 */
@Slf4j
@RestController
@RequestMapping("/impact-analysis")
@RequiredArgsConstructor
@Tag(name = "Impact Analysis", description = "Compare quality metrics before/after quality gate tool introduction")
@CrossOrigin(origins = "*")
public class ImpactAnalysisController implements ApiV1Controller {

    private final ImpactAnalysisService impactAnalysisService;

    /**
     * Run (or return the cached) impact analysis for a repository.
     * Results are cached — subsequent calls return cached data unless forceNewAnalysis=true.
     */
    @PostMapping
    @Operation(
            summary = "Run Impact Analysis",
            description = "Determines whether code quality improved after quality gate tool introduction, " +
                    "using the existing detection/history services and the ingested SonarQube metrics dataset. " +
                    "Returns hasQualityGate=false without persisting anything if the repository has no detected " +
                    "quality gate. Results are cached; use forceNewAnalysis=true to recompute."
    )
    public ResponseEntity<ApiResponse<ImpactAnalysisResponse>> analyze(
            @Parameter(description = "Repository owner") @RequestParam String owner,
            @Parameter(description = "Repository name") @RequestParam String repo,
            @Parameter(description = "Force recomputation even if cached data exists")
            @RequestParam(defaultValue = "false") boolean forceNewAnalysis) {

        log.info("Impact analysis request for {}/{} (forceNewAnalysis={})", owner, repo, forceNewAnalysis);

        try {
            ImpactAnalysisResponse result = impactAnalysisService.analyze(owner, repo, forceNewAnalysis);

            String message;
            if (!result.isHasQualityGate()) {
                message = "No quality gate detected for " + owner + "/" + repo;
            } else if (result.isCached()) {
                message = "Impact analysis retrieved from cache";
            } else {
                message = "Impact analysis completed";
            }

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
            log.error("Impact analysis failed for {}/{}", owner, repo, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<ImpactAnalysisResponse>builder()
                            .success(false)
                            .error("Impact analysis failed: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Get a previously computed impact analysis for a repository, structured for UI
     * visualization (timeline chart + before/after comparison cards).
     */
    @GetMapping("/{owner}/{repo}")
    @Operation(
            summary = "Get Impact Analysis",
            description = "Retrieve a previously computed impact analysis. Returns 404 if none exists yet."
    )
    public ResponseEntity<ApiResponse<ImpactAnalysisResponse>> getImpactAnalysis(
            @PathVariable String owner,
            @PathVariable String repo) {

        log.info("Get impact analysis request for {}/{}", owner, repo);

        ImpactAnalysisResponse result = impactAnalysisService.getAnalysis(owner, repo);

        return ResponseEntity.ok(ApiResponse.<ImpactAnalysisResponse>builder()
                .success(true)
                .message("Impact analysis retrieved")
                .data(result)
                .build());
    }

    /**
     * List all repositories with a computed impact analysis, with summary stats.
     */
    @GetMapping
    @Operation(
            summary = "List Impact Analyses",
            description = "Get a summary of all repositories that have a computed impact analysis."
    )
    public ResponseEntity<ApiResponse<List<ImpactAnalysisSummaryDto>>> listImpactAnalyses() {
        List<ImpactAnalysisSummaryDto> results = impactAnalysisService.listAll();

        log.info("List impact analyses request ({} found)", results.size());

        return ResponseEntity.ok(ApiResponse.<List<ImpactAnalysisSummaryDto>>builder()
                .success(true)
                .message("Found " + results.size() + " repositories")
                .data(results)
                .build());
    }
}
