package com.thesis.qualitygateanalyzer.dto.response;

import com.thesis.qualitygateanalyzer.config.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void builderDefaults_setTimestampAutomatically() {
        ApiResponse<String> response = ApiResponse.<String>builder().success(true).build();
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void builderDefaults_produceFreshTimestampPerBuild() throws InterruptedException {
        ApiResponse<String> first = ApiResponse.<String>builder().build();
        Thread.sleep(5);
        ApiResponse<String> second = ApiResponse.<String>builder().build();
        assertThat(second.getTimestamp()).isAfterOrEqualTo(first.getTimestamp());
    }

    @Test
    void correlationId_pulledFromMdcWhenPresent() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "test-correlation-id");
        try {
            ApiResponse<String> response = ApiResponse.<String>builder().build();
            assertThat(response.getCorrelationId()).isEqualTo("test-correlation-id");
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    @Test
    void correlationId_nullWhenMdcEmpty() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
        ApiResponse<String> response = ApiResponse.<String>builder().build();
        assertThat(response.getCorrelationId()).isNull();
    }

    @Test
    void errorsList_canBeSetForMultipleValidationFailures() {
        ApiError e1 = ApiError.builder().field("owner").message("required").build();
        ApiResponse<Void> response = ApiResponse.<Void>builder().success(false).errors(List.of(e1)).build();
        assertThat(response.getErrors()).containsExactly(e1);
        assertThat(e1.getField()).isEqualTo("owner");
        assertThat(e1.getMessage()).isEqualTo("required");
    }

    @Test
    void explicitTimestamp_overridesDefault() {
        LocalDateTime fixed = LocalDateTime.of(2024, 1, 1, 0, 0);
        ApiResponse<String> response = ApiResponse.<String>builder().timestamp(fixed).build();
        assertThat(response.getTimestamp()).isEqualTo(fixed);
    }
}
