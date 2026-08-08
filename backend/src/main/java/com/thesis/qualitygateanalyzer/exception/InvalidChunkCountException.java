package com.thesis.qualitygateanalyzer.exception;

/**
 * Thrown when the requested chunk count exceeds the total number of commits
 * available for a repository. Mapped to HTTP 400 by the global exception handler.
 */
public class InvalidChunkCountException extends QualityGateException {

    public InvalidChunkCountException(String message) {
        super(message);
    }
}
