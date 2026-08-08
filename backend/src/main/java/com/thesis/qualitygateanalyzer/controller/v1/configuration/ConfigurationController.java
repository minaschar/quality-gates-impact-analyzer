package com.thesis.qualitygateanalyzer.controller.v1.configuration;

import com.thesis.qualitygateanalyzer.controller.v1.ApiV1Controller;
import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity.ConfigCategory;
import com.thesis.qualitygateanalyzer.service.configuration.ConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for managing runtime configuration.
 * Allows viewing and updating configuration values from the UI.
 */
@Slf4j
@RestController
@RequestMapping("/configuration")
@RequiredArgsConstructor
@Tag(name = "Configuration", description = "Manage runtime configuration values")
@CrossOrigin(origins = "*")
public class ConfigurationController implements ApiV1Controller {

    private final ConfigurationService configurationService;

    // READ OPERATIONS

    /**
     * Get all configuration values.
     */
    @GetMapping
    @Operation(summary = "Get All Configuration", description = "Retrieve all configuration values")
    public ResponseEntity<ApiResponse<List<ConfigurationEntity>>> getAll() {
        List<ConfigurationEntity> configs = configurationService.findAll();

        // Mask sensitive values
        configs.forEach(c -> {
            if (c.getConfigKey().contains("TOKEN") || c.getConfigKey().contains("SECRET")) {
                String value = c.getConfigValue();
                if (value != null && value.length() > 4) {
                    c.setConfigValue("***" + value.substring(value.length() - 4));
                }
            }
        });

        return ResponseEntity.ok(ApiResponse.<List<ConfigurationEntity>>builder()
                .success(true)
                .message("Found " + configs.size() + " configuration entries")
                .data(configs)
                .build());
    }

    /**
     * Get configuration by category.
     */
    @GetMapping("/category/{category}")
    @Operation(summary = "Get Configuration by Category",
            description = "Retrieve configuration values for a specific category (API, LIMITS, FEATURES)")
    public ResponseEntity<ApiResponse<List<ConfigurationEntity>>> getByCategory(
            @PathVariable String category) {

        try {
            ConfigCategory cat = ConfigCategory.valueOf(category.toUpperCase());
            List<ConfigurationEntity> configs = configurationService.findByCategory(cat);

            return ResponseEntity.ok(ApiResponse.<List<ConfigurationEntity>>builder()
                    .success(true)
                    .message("Found " + configs.size() + " entries for category " + category)
                    .data(configs)
                    .build());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<ConfigurationEntity>>builder()
                            .success(false)
                            .error("Invalid category: " + category + ". Valid values: API, LIMITS, FEATURES")
                            .build());
        }
    }

    /**
     * Get a specific configuration value.
     */
    @GetMapping("/{key}")
    @Operation(summary = "Get Configuration Value", description = "Retrieve a specific configuration value by key")
    public ResponseEntity<ApiResponse<ConfigurationEntity>> getByKey(
            @PathVariable String key) {

        return configurationService.findByKey(key)
                .map(config -> {
                    // Mask sensitive values
                    if (key.contains("TOKEN") || key.contains("SECRET")) {
                        String value = config.getConfigValue();
                        if (value != null && value.length() > 4) {
                            config.setConfigValue("***" + value.substring(value.length() - 4));
                        }
                    }
                    return ResponseEntity.ok(ApiResponse.<ConfigurationEntity>builder()
                            .success(true)
                            .data(config)
                            .build());
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.<ConfigurationEntity>builder()
                                .success(false)
                                .error("Configuration not found: " + key)
                                .build()));
    }

    // UPDATE OPERATIONS

    /**
     * Update a configuration value.
     */
    @PutMapping("/{key}")
    @Operation(summary = "Update Configuration Value", description = "Update a configuration value by key")
    public ResponseEntity<ApiResponse<ConfigurationEntity>> updateValue(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {

        String newValue = body.get("value");
        if (newValue == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConfigurationEntity>builder()
                            .success(false)
                            .error("Request body must contain 'value' field")
                            .build());
        }

        try {
            ConfigurationEntity updated = configurationService.updateValue(key, newValue);

            // Mask sensitive values in response
            if (key.contains("TOKEN") || key.contains("SECRET")) {
                updated.setConfigValue("***" + newValue.substring(Math.max(0, newValue.length() - 4)));
            }

            return ResponseEntity.ok(ApiResponse.<ConfigurationEntity>builder()
                    .success(true)
                    .message("Configuration updated: " + key)
                    .data(updated)
                    .build());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConfigurationEntity>builder()
                            .success(false)
                            .error(e.getMessage())
                            .build());
        }
    }

    /**
     * Batch update multiple configuration values.
     */
    @PutMapping("/batch")
    @Operation(summary = "Batch Update Configuration",
            description = "Update multiple configuration values at once")
    public ResponseEntity<ApiResponse<Map<String, String>>> batchUpdate(
            @RequestBody Map<String, String> updates) {

        log.info("Batch update request for {} configurations", updates.size());

        Map<String, String> results = new java.util.HashMap<>();

        for (Map.Entry<String, String> entry : updates.entrySet()) {
            try {
                configurationService.updateValue(entry.getKey(), entry.getValue());
                results.put(entry.getKey(), "OK");
            } catch (Exception e) {
                results.put(entry.getKey(), "ERROR: " + e.getMessage());
            }
        }

        boolean allSuccess = results.values().stream().allMatch(v -> v.equals("OK"));

        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .success(allSuccess)
                .message(allSuccess ? "All configurations updated" : "Some updates failed")
                .data(results)
                .build());
    }

    // CACHE OPERATIONS

    /**
     * Refresh the configuration cache.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh Cache", description = "Refresh the in-memory configuration cache")
    public ResponseEntity<ApiResponse<Void>> refreshCache() {
        configurationService.refreshCache();

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Configuration cache refreshed")
                .build());
    }

    // CONVENIENCE ENDPOINTS

    /**
     * Get current limit values (convenience endpoint).
     */
    @GetMapping("/limits")
    @Operation(summary = "Get Current Limits", description = "Get all current limit values with their effective values")
    public ResponseEntity<Map<String, Integer>> getLimits() {
        return ResponseEntity.ok(Map.of(
                "SAMPLE_PRS_LIMIT", configurationService.getSamplePrsLimit(),
                "WORKFLOW_RUNS_LIMIT", configurationService.getWorkflowRunsLimit(),
                "PR_ANALYSIS_LIMIT", configurationService.getPrAnalysisLimit(),
                "CHECK_RUNS_PER_COMMIT", configurationService.getCheckRunsPerCommit(),
                "MAX_BINARY_SEARCH_ITERATIONS", configurationService.getMaxBinarySearchIterations(),
                "LINEAR_SEARCH_THRESHOLD", configurationService.getLinearSearchThreshold(),
                "SAMPLE_PRS_TO_RETURN", configurationService.getSamplePrsToReturn()
        ));
    }

    /**
     * Get current feature flags (convenience endpoint).
     */
    @GetMapping("/features")
    @Operation(summary = "Get Feature Flags", description = "Get all feature flag values")
    public ResponseEntity<Map<String, Boolean>> getFeatures() {
        return ResponseEntity.ok(Map.of(
                "ENABLE_PR_FALLBACK", configurationService.isPrFallbackEnabled(),
                "ENABLE_HISTORY_ANALYSIS", configurationService.isHistoryAnalysisEnabled(),
                "ENABLE_EXTERNAL_CHECK_DETECTION", configurationService.isExternalCheckDetectionEnabled()
        ));
    }
}
