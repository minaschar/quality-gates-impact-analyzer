package com.thesis.qualitygateanalyzer.exception;

import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Central mapping from application exceptions to HTTP responses.
 * <p>
 * Each handler here reproduces the exact status code and body shape the
 * corresponding controller previously built by hand, so introducing this
 * class does not change any existing response contract.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(QualityGateDetectionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleQualityGateDetectionNotFound(QualityGateDetectionNotFoundException e) {
        log.warn("Detection not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder().success(false).error(e.getMessage()).build());
    }

    @ExceptionHandler(QualityMetricsNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleQualityMetricsNotFound(QualityMetricsNotFoundException e) {
        log.warn("Quality metrics not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder().success(false).error(e.getMessage()).build());
    }

    @ExceptionHandler(ImpactAnalysisNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleImpactAnalysisNotFound(ImpactAnalysisNotFoundException e) {
        log.warn("Impact analysis not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder().success(false).error(e.getMessage()).build());
    }

    @ExceptionHandler(NoDataInDatasetException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoDataInDataset(NoDataInDatasetException e) {
        log.warn("No dataset rows: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder().success(false).error(e.getMessage()).build());
    }

    @ExceptionHandler(RepositoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRepositoryNotFound(RepositoryNotFoundException e) {
        log.warn("Repository not found: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>builder().success(false).error(e.getMessage()).build());
    }

    /**
     * Preserves the pre-existing non-ApiResponse shape used by the commit-chunking
     * endpoint for this specific error, so existing clients parsing {@code error}/
     * {@code message} fields are unaffected.
     */
    @ExceptionHandler(InvalidChunkCountException.class)
    public ResponseEntity<Map<String, String>> handleInvalidChunkCount(InvalidChunkCountException e) {
        log.warn("Invalid chunk count: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_CHUNK_COUNT",
                "message", e.getMessage()
        ));
    }

    @ExceptionHandler(QualityGateException.class)
    public ResponseEntity<ApiResponse<Void>> handleQualityGateException(QualityGateException e) {
        log.error("Unhandled application error", e);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.<Void>builder().success(false).error(e.getMessage()).build());
    }
}
