package com.thesis.qualitygateanalyzer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.thesis.qualitygateanalyzer.config.CorrelationIdFilter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Generic API response wrapper.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String error;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Builder.Default
    private String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

    private List<ApiError> errors;
}
