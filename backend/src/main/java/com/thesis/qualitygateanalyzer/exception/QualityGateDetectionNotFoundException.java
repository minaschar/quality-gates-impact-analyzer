package com.thesis.qualitygateanalyzer.exception;

/**
 * Thrown when no stored detection exists for the requested repository.
 * Mapped to HTTP 404 by the global exception handler.
 */
public class QualityGateDetectionNotFoundException extends QualityGateException {

    public QualityGateDetectionNotFoundException(String message) {
        super(message);
    }
}
