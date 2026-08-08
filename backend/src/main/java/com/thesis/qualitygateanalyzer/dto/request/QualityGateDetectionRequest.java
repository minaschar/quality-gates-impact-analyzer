package com.thesis.qualitygateanalyzer.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for quality gate detection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QualityGateDetectionRequest {
    private String repositoryUrl;
}
