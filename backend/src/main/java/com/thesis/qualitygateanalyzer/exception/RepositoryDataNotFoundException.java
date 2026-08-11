package com.thesis.qualitygateanalyzer.exception;

/**
 * Thrown when none of a repository's data -- detection, commit history, quality metrics, or
 * impact analysis -- exists for the requested owner/repo. Mapped to HTTP 404 by the global
 * exception handler.
 */
public class RepositoryDataNotFoundException extends QualityGateException {

    public RepositoryDataNotFoundException(String message) {
        super(message);
    }
}
