package com.thesis.qualitygateanalyzer.service.qualitymetrics;

import org.apache.commons.csv.CSVRecord;

import java.util.List;
import java.util.Map;

/**
 * Streams the software quality metrics dataset and filters rows for a single repository.
 */
public interface QualityMetricsCsvLoader {

    record RepoKey(String owner, String repo) {
    }

    /**
     * Load all dataset rows belonging to a single repository.
     */
    List<CSVRecord> loadRowsForRepository(String owner, String repo);

    /**
     * Groups every row in the dataset by repository in a single pass, for bulk ingestion.
     */
    Map<RepoKey, List<CSVRecord>> loadAllGroupedByRepository();
}
