package com.thesis.qualitygateanalyzer.controller.v1.diagnostics;

import com.thesis.qualitygateanalyzer.controller.v1.ApiV1Controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Application health check, independent of any single feature area. Alongside
 * {@link GitHubController} in this package: neither has business domain logic of its own --
 * both just report on the state of the system or a dependency it relies on.
 */
@RestController
@Tag(name = "Health", description = "Application health check")
@CrossOrigin(origins = "*")
public class HealthController implements ApiV1Controller {

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "version", "1.0.0",
                "database", "PostgreSQL"
        ));
    }
}
