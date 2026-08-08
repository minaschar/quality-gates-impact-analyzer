package com.thesis.qualitygateanalyzer.service.configuration;

import com.thesis.qualitygateanalyzer.constant.ConfigurationConstants;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity.ConfigCategory;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity.DataType;
import com.thesis.qualitygateanalyzer.repository.configuration.ConfigurationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link ConfigurationService} implementation.
 * Provides caching and type-safe access to configuration values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationServiceImpl implements ConfigurationService {

    private final ConfigurationRepository repository;

    // In-memory cache for frequently accessed values
    private final Map<String, Object> localCache = new ConcurrentHashMap<>();

    // Initialization

    @PostConstruct
    public void init() {
        log.info("ConfigurationService initialized - loading configuration into cache");
        refreshCache();
    }

    @Override
    @CacheEvict(value = "configuration", allEntries = true)
    public void refreshCache() {
        localCache.clear();
        List<ConfigurationEntity> configs = repository.findAll();
        for (ConfigurationEntity config : configs) {
            Object value = parseValue(config);
            if (value != null) {
                localCache.put(config.getConfigKey(), value);
            }
        }
        log.info("Configuration cache refreshed: {} entries", localCache.size());
    }

    private Object parseValue(ConfigurationEntity config) {
        if (config.getConfigValue() == null) return null;
        return switch (config.getDataType()) {
            case STRING -> config.getStringValue();
            case INTEGER -> config.getIntegerValue();
            case BOOLEAN -> config.getBooleanValue();
            case DOUBLE -> config.getDoubleValue();
        };
    }

    // Value getters

    @Override
    public String getString(String key, String defaultValue) {
        Object cached = localCache.get(key);
        if (cached instanceof String s) {
            return s;
        }
        return repository.findByConfigKey(key)
                .map(ConfigurationEntity::getStringValue)
                .orElse(defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        Object cached = localCache.get(key);
        if (cached instanceof Integer i) {
            return i;
        }
        return repository.findByConfigKey(key)
                .map(ConfigurationEntity::getIntegerValue)
                .orElse(defaultValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        Object cached = localCache.get(key);
        if (cached instanceof Boolean b) {
            return b;
        }
        return repository.findByConfigKey(key)
                .map(ConfigurationEntity::getBooleanValue)
                .orElse(defaultValue);
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        Object cached = localCache.get(key);
        if (cached instanceof Double d) {
            return d;
        }
        return repository.findByConfigKey(key)
                .map(ConfigurationEntity::getDoubleValue)
                .orElse(defaultValue);
    }

    // Convenience getters

    @Override
    public String getGitHubToken() {
        return getString(ConfigurationConstants.GITHUB_TOKEN, "");
    }

    @Override
    public int getSamplePrsLimit() {
        return getInt(ConfigurationConstants.SAMPLE_PRS_LIMIT, 100);
    }

    @Override
    public int getWorkflowRunsLimit() {
        return getInt(ConfigurationConstants.WORKFLOW_RUNS_LIMIT, 100);
    }

    @Override
    public int getPrAnalysisLimit() {
        return getInt(ConfigurationConstants.PR_ANALYSIS_LIMIT, 100);
    }

    @Override
    public int getCheckRunsPerCommit() {
        return getInt(ConfigurationConstants.CHECK_RUNS_PER_COMMIT, 100);
    }

    @Override
    public int getMaxBinarySearchIterations() {
        return getInt(ConfigurationConstants.MAX_BINARY_SEARCH_ITERATIONS, 50);
    }

    @Override
    public int getLinearSearchThreshold() {
        return getInt(ConfigurationConstants.LINEAR_SEARCH_THRESHOLD, 10);
    }

    @Override
    public int getSamplePrsToReturn() {
        return getInt(ConfigurationConstants.SAMPLE_PRS_TO_RETURN, 5);
    }

    @Override
    public boolean isPrFallbackEnabled() {
        return getBoolean(ConfigurationConstants.ENABLE_PR_FALLBACK, true);
    }

    @Override
    public boolean isHistoryAnalysisEnabled() {
        return getBoolean(ConfigurationConstants.ENABLE_HISTORY_ANALYSIS, true);
    }

    @Override
    public boolean isExternalCheckDetectionEnabled() {
        return getBoolean(ConfigurationConstants.ENABLE_EXTERNAL_CHECK_DETECTION, true);
    }

    // CRUD operations

    @Override
    @Cacheable(value = "configuration", key = "'all'")
    public List<ConfigurationEntity> findAll() {
        return repository.findAllByOrderByCategoryAscConfigKeyAsc();
    }

    @Override
    public List<ConfigurationEntity> findByCategory(ConfigCategory category) {
        return repository.findByCategory(category);
    }

    @Override
    public Optional<ConfigurationEntity> findByKey(String key) {
        return repository.findByConfigKey(key);
    }

    @Override
    @Transactional
    @CacheEvict(value = "configuration", allEntries = true)
    public ConfigurationEntity updateValue(String key, String newValue) {
        ConfigurationEntity config = repository.findByConfigKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Configuration not found: " + key));

        // Validate value based on data type
        validateValue(config.getDataType(), newValue);

        config.setConfigValue(newValue);
        ConfigurationEntity saved = repository.save(config);

        // Update local cache
        Object parsed = parseValue(saved);
        if (parsed != null) {
            localCache.put(key, parsed);
        }

        log.info("Configuration updated: {} = {}", key,
                key.contains("TOKEN") ? "***" : newValue);

        return saved;
    }

    @Override
    @Transactional
    @CacheEvict(value = "configuration", allEntries = true)
    public ConfigurationEntity create(String key, String value, DataType dataType,
                                      ConfigCategory category, String description) {
        if (repository.existsByConfigKey(key)) {
            throw new IllegalArgumentException("Configuration already exists: " + key);
        }

        validateValue(dataType, value);

        ConfigurationEntity config = ConfigurationEntity.builder()
                .configKey(key)
                .configValue(value)
                .dataType(dataType)
                .category(category)
                .description(description)
                .build();

        ConfigurationEntity saved = repository.save(config);

        // Update local cache
        Object parsed = parseValue(saved);
        if (parsed != null) {
            localCache.put(key, parsed);
        }

        log.info("Configuration created: {} = {}", key, value);

        return saved;
    }

    @Override
    @Transactional
    @CacheEvict(value = "configuration", allEntries = true)
    public void delete(String key) {
        repository.deleteByConfigKey(key);
        localCache.remove(key);
        log.info("Configuration deleted: {}", key);
    }

    // Validation

    private void validateValue(DataType dataType, String value) {
        if (value == null || value.isBlank()) {
            return; // Allow empty values
        }

        try {
            switch (dataType) {
                case INTEGER -> Integer.parseInt(value);
                case BOOLEAN -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException("Invalid boolean: " + value);
                    }
                }
                case DOUBLE -> Double.parseDouble(value);
                case STRING -> { /* Always valid */ }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid value for type " + dataType + ": " + value);
        }
    }
}
