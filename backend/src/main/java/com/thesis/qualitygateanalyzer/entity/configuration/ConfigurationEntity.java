package com.thesis.qualitygateanalyzer.entity.configuration;

import com.thesis.qualitygateanalyzer.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Entity for storing runtime configuration values.
 * These values can be edited from the UI.
 */
@Entity
@Table(name = "t_configuration", indexes = {
        @Index(name = "idx_configuration_key", columnList = "config_key"),
        @Index(name = "idx_configuration_category", columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigurationEntity extends BaseEntity {

    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private DataType dataType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ConfigCategory category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ENUMS

    public enum DataType {
        STRING, INTEGER, BOOLEAN, DOUBLE
    }

    public enum ConfigCategory {
        API, LIMITS, FEATURES
    }

    // VALUE ACCESSORS

    /**
     * Get value as String.
     */
    public String getStringValue() {
        return configValue;
    }

    /**
     * Get value as Integer.
     */
    public Integer getIntegerValue() {
        if (configValue == null || configValue.isBlank()) return null;
        try {
            return Integer.parseInt(configValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Get value as Boolean.
     */
    public Boolean getBooleanValue() {
        if (configValue == null || configValue.isBlank()) return null;
        return Boolean.parseBoolean(configValue);
    }

    /**
     * Get value as Double.
     */
    public Double getDoubleValue() {
        if (configValue == null || configValue.isBlank()) return null;
        try {
            return Double.parseDouble(configValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
