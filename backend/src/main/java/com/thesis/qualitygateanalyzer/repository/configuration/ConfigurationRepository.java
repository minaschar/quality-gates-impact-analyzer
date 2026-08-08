package com.thesis.qualitygateanalyzer.repository.configuration;

import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity.ConfigCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for configuration entities.
 */
@Repository
public interface ConfigurationRepository extends JpaRepository<ConfigurationEntity, UUID> {

    /**
     * Find configuration by key.
     */
    Optional<ConfigurationEntity> findByConfigKey(String configKey);

    /**
     * Check if configuration exists by key.
     */
    boolean existsByConfigKey(String configKey);

    /**
     * Find all configurations by category.
     */
    List<ConfigurationEntity> findByCategory(ConfigCategory category);

    /**
     * Find all configurations ordered by category and key.
     */
    List<ConfigurationEntity> findAllByOrderByCategoryAscConfigKeyAsc();

    /**
     * Delete configuration by key.
     */
    void deleteByConfigKey(String configKey);
}
