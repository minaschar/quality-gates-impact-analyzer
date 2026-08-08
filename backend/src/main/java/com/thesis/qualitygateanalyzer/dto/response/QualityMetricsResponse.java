package com.thesis.qualitygateanalyzer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class QualityMetricsResponse {

    private String organization;
    private String repo;
    private int totalRecords;
    private List<QualityMetricSnapshotDto> records;
}
