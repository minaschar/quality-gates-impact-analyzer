package com.thesis.qualitygateanalyzer.exception;

/**
 * Thrown when no previously ingested quality metrics exist for a repository.
 * Mapped to HTTP 404 by the global exception handler.
 */
public class QualityMetricsNotFoundException extends QualityGateException {

    public QualityMetricsNotFoundException(String message) {
        super(message);
    }
}
