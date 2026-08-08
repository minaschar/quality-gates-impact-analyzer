package com.thesis.qualitygateanalyzer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * A single structured error item, used when an {@link ApiResponse} needs to
 * report more than one problem at once (e.g. multiple validation failures).
 */
@Getter
@Builder
@AllArgsConstructor
public class ApiError {

    private String field;
    private String message;
}
