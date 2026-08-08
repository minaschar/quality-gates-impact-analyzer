package com.thesis.qualitygateanalyzer.exception;

/**
 * Represents an error in a runtime configuration value or key.
 * <p>
 * Not currently thrown: {@code ConfigurationController} has two independent,
 * pre-existing "not found" behaviors (404 on GET by key, 400 on PUT of an
 * unknown key) that map to the same message but different statuses. Unifying
 * them under one exception type would require changing one of those statuses,
 * so both call sites keep their existing manual handling. Available for future
 * configuration error cases that don't have this ambiguity.
 */
public class ConfigurationException extends QualityGateException {

    public ConfigurationException(String message) {
        super(message);
    }
}
