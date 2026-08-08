package com.thesis.qualitygateanalyzer.service.qualitymetrics;

import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real classpath dataset (src/main/resources/data/filtered_software_quality_metrics.csv),
 * since QualityMetricsCsvLoaderImpl has no injectable resource path to substitute a test fixture.
 * "apache/cordova-browser" is a known-present repository in that dataset (102 rows).
 */
class QualityMetricsCsvLoaderImplTest {

    private final QualityMetricsCsvLoaderImpl loader = new QualityMetricsCsvLoaderImpl();

    @Test
    void loadRowsForRepository_knownRepo_returnsMatchingRows() {
        List<CSVRecord> rows = loader.loadRowsForRepository("apache", "cordova-browser");
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.get("username")).isEqualToIgnoringCase("apache");
            assertThat(r.get("repositoryName")).isEqualToIgnoringCase("cordova-browser");
        });
    }

    @Test
    void loadRowsForRepository_isCaseInsensitive() {
        List<CSVRecord> rows = loader.loadRowsForRepository("APACHE", "CORDOVA-BROWSER");
        assertThat(rows).isNotEmpty();
    }

    @Test
    void loadRowsForRepository_unknownRepo_returnsEmpty() {
        List<CSVRecord> rows = loader.loadRowsForRepository("nonexistent-owner-xyz", "nonexistent-repo-xyz");
        assertThat(rows).isEmpty();
    }

    @Test
    void loadAllGroupedByRepository_groupsKnownRepoTogether() {
        Map<QualityMetricsCsvLoader.RepoKey, List<CSVRecord>> grouped = loader.loadAllGroupedByRepository();
        assertThat(grouped).isNotEmpty();
        QualityMetricsCsvLoader.RepoKey key = new QualityMetricsCsvLoader.RepoKey("apache", "cordova-browser");
        assertThat(grouped).containsKey(key);
        assertThat(grouped.get(key)).isNotEmpty();
    }
}
