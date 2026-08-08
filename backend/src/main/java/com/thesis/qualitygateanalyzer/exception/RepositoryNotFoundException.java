package com.thesis.qualitygateanalyzer.exception;

/**
 * Thrown when the target GitHub repository does not exist or is inaccessible.
 * <p>
 * Mapped to HTTP 400 (not 404) by the global exception handler: this preserves
 * the existing /analyze contract, which has always treated an unresolvable
 * repository URL/repository as a bad request alongside URL validation errors.
 */
public class RepositoryNotFoundException extends QualityGateException {

    public RepositoryNotFoundException(String message) {
        super(message);
    }
}
