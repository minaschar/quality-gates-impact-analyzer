package com.thesis.qualitygateanalyzer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class BulkIngestSummary {

    private int totalRepositoriesInDataset;
    private int repositoriesIngested;
    private int repositoriesFromCache;
    private int totalRecordsIngested;
}
