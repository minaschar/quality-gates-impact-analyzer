package com.thesis.qualitygateanalyzer.exception;

/**
 * Thrown when the source software-quality-metrics dataset contains no rows
 * for the requested repository. Mapped to HTTP 404 by the global exception handler.
 */
public class NoDataInDatasetException extends QualityGateException {

    public NoDataInDatasetException(String message) {
        super(message);
    }
}
