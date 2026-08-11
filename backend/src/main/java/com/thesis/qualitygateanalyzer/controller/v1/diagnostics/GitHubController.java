package com.thesis.qualitygateanalyzer.controller.v1.diagnostics;

import com.thesis.qualitygateanalyzer.controller.v1.ApiV1Controller;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GitHub API diagnostics, independent of any single feature area that happens to call GitHub.
 * Alongside {@link HealthController} in this package: neither has business domain logic of its
 * own -- both just report on the state of the system or a dependency it relies on.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "GitHub", description = "GitHub API diagnostics")
@CrossOrigin(origins = "*")
public class GitHubController implements ApiV1Controller {

    private final GitHubApiClient githubClient;

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
}
