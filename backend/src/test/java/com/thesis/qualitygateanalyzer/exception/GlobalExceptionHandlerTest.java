package com.thesis.qualitygateanalyzer.exception;

import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void detectionNotFound_mapsTo404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleQualityGateDetectionNotFound(new QualityGateDetectionNotFoundException("no analysis"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError()).isEqualTo("no analysis");
    }

    @Test
    void qualityMetricsNotFound_mapsTo404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleQualityMetricsNotFound(new QualityMetricsNotFoundException("no metrics"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().getError()).isEqualTo("no metrics");
    }

    @Test
    void noDataInDataset_mapsTo404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNoDataInDataset(new NoDataInDatasetException("no rows"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().getError()).isEqualTo("no rows");
    }

    @Test
    void repositoryNotFound_mapsTo400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleRepositoryNotFound(new RepositoryNotFoundException("repo missing"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().getError()).isEqualTo("repo missing");
    }

    @Test
    void invalidChunkCount_mapsTo400WithPlainMapBody() {
        ResponseEntity<Map<String, String>> response =
                handler.handleInvalidChunkCount(new InvalidChunkCountException("too many chunks"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "INVALID_CHUNK_COUNT")
                .containsEntry("message", "too many chunks");
    }

    @Test
    void genericQualityGateException_mapsTo500() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleQualityGateException(new QualityGateException("unexpected"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().getError()).isEqualTo("unexpected");
    }
}
