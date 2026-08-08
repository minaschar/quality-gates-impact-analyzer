package com.thesis.qualitygateanalyzer.exception;

/**
 * Thrown when no stored impact analysis exists for the requested repository.
 * Mapped to HTTP 404 by the global exception handler.
 */
public class ImpactAnalysisNotFoundException extends QualityGateException {

    public ImpactAnalysisNotFoundException(String message) {
        super(message);
    }
}
