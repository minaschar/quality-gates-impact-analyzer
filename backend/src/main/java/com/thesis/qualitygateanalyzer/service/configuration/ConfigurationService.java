package com.thesis.qualitygateanalyzer.service.configuration;

import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity.ConfigCategory;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity.DataType;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing runtime configuration.
 * Provides caching and type-safe access to configuration values.
 */
public interface ConfigurationService {

    /**
     * Refresh the local cache from database.
     */
    void refreshCache();

    /**
     * Get string value with default.
     */
    String getString(String key, String defaultValue);

    /**
     * Get integer value with default.
     */
    int getInt(String key, int defaultValue);

    /**
     * Get boolean value with default.
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * Get double value with default.
     */
    double getDouble(String key, double defaultValue);

    String getGitHubToken();

    int getSamplePrsLimit();

    int getWorkflowRunsLimit();

    int getPrAnalysisLimit();

    int getCheckRunsPerCommit();

    int getMaxBinarySearchIterations();

    int getLinearSearchThreshold();

    int getSamplePrsToReturn();

    boolean isPrFallbackEnabled();

    boolean isHistoryAnalysisEnabled();

    boolean isExternalCheckDetectionEnabled();

    /**
     * Get all configurations.
     */
    List<ConfigurationEntity> findAll();

    /**
     * Get configurations by category.
     */
    List<ConfigurationEntity> findByCategory(ConfigCategory category);

    /**
     * Get single configuration by key.
     */
    Optional<ConfigurationEntity> findByKey(String key);

    /**
     * Update configuration value.
     */
    ConfigurationEntity updateValue(String key, String newValue);

    /**
     * Create new configuration.
     */
    ConfigurationEntity create(String key, String value, DataType dataType,
                               ConfigCategory category, String description);

    /**
     * Delete configuration by key.
     */
    void delete(String key);
}
