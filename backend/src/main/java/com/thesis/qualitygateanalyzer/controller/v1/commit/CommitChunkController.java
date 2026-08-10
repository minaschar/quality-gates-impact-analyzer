package com.thesis.qualitygateanalyzer.controller.v1.commit;

import com.thesis.qualitygateanalyzer.controller.v1.ApiV1Controller;
import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.dto.response.CommitChunkResponse;
import com.thesis.qualitygateanalyzer.exception.InvalidChunkCountException;
import com.thesis.qualitygateanalyzer.service.commit.CommitChunkService;
import com.thesis.qualitygateanalyzer.util.GitHubIdentifiers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Commit Chunking", description = "Deterministic temporal downsampling of repository commit history into fixed-size chunks")
@CrossOrigin(origins = "*")
public class CommitChunkController implements ApiV1Controller {

    private final CommitChunkService commitChunkService;

    @GetMapping("/repositories/{organization}/{repo}/commits/chunks")
    @Operation(
            summary = "Get Repository Commit Chunks",
            description = "Retrieves the full commit history of a repository's default branch and compresses it " +
                    "into a fixed number of sequential chunks. Each chunk is represented only by its oldest " +
                    "(first) commit. History is cached in the database; use forceRefresh=true to re-fetch."
    )
    public ResponseEntity<?> getCommitChunks(
            @PathVariable String organization,
            @PathVariable String repo,
            @Parameter(description = "Number of desired chunks. Must be <= total commits.")
            @RequestParam(defaultValue = "100") int chunkCount,
            @Parameter(description = "Force re-fetch from GitHub even if cached data exists.")
            @RequestParam(defaultValue = "false") boolean forceRefresh) {

        organization = GitHubIdentifiers.normalize(organization);
        repo = GitHubIdentifiers.normalize(repo);

        log.info("Commit chunk request for {}/{} (chunkCount={}, forceRefresh={})", organization, repo, chunkCount, forceRefresh);

        try {
            CommitChunkResponse response = commitChunkService.getChunks(organization, repo, chunkCount, forceRefresh);
            return ResponseEntity.ok(ApiResponse.<CommitChunkResponse>builder()
                    .success(true)
                    .message("Commit chunks computed successfully")
                    .data(response)
                    .build());

        } catch (IllegalArgumentException e) {
            log.warn("Bad request for {}/{}: {}", organization, repo, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.<CommitChunkResponse>builder()
                    .success(false)
                    .error(e.getMessage())
                    .build());

        } catch (InvalidChunkCountException e) {
            // Let the global exception handler build the response (same shape/status as before).
            throw e;

        } catch (Exception e) {
            log.error("Failed to compute commit chunks for {}/{}", organization, repo, e);
            return ResponseEntity.internalServerError().body(ApiResponse.<CommitChunkResponse>builder()
                    .success(false)
                    .error("Failed to compute commit chunks: " + e.getMessage())
                    .build());
        }
    }
}
