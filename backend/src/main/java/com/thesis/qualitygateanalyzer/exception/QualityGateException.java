package com.thesis.qualitygateanalyzer.exception;

/**
 * Base exception for all application-specific errors.
 */
public class QualityGateException extends RuntimeException {

    public QualityGateException(String message) {
        super(message);
    }

    public QualityGateException(String message, Throwable cause) {
        super(message, cause);
    }
}
