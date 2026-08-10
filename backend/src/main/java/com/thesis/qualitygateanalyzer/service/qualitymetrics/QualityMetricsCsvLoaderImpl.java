package com.thesis.qualitygateanalyzer.service.qualitymetrics;

import com.thesis.qualitygateanalyzer.util.GitHubIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link QualityMetricsCsvLoader} implementation.
 * The dataset (~45MB, ~350 repositories) is not cached in memory: it is re-scanned only on
 * a database cache-miss, so the cost is paid once per repository (results are then persisted).
 */
@Slf4j
@Component
public class QualityMetricsCsvLoaderImpl implements QualityMetricsCsvLoader {

    private static final String CSV_CLASSPATH_PATH = "data/filtered_software_quality_metrics.csv";

    @Override
    public List<CSVRecord> loadRowsForRepository(String owner, String repo) {
        List<CSVRecord> matches = new ArrayList<>();

        try (CSVParser parser = parse()) {
            for (CSVRecord record : parser) {
                if (owner.equalsIgnoreCase(record.get("username"))
                        && repo.equalsIgnoreCase(record.get("repositoryName"))) {
                    matches.add(record);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read quality metrics dataset: " + e.getMessage(), e);
        }

        log.info("Found {} quality metric rows for {}/{} in dataset", matches.size(), owner, repo);
        return matches;
    }

    @Override
    public Map<RepoKey, List<CSVRecord>> loadAllGroupedByRepository() {
        Map<RepoKey, List<CSVRecord>> grouped = new LinkedHashMap<>();

        try (CSVParser parser = parse()) {
            for (CSVRecord record : parser) {
                RepoKey key = new RepoKey(
                        GitHubIdentifiers.normalize(record.get("username")),
                        GitHubIdentifiers.normalize(record.get("repositoryName"))
                );
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read quality metrics dataset: " + e.getMessage(), e);
        }

        log.info("Grouped dataset into {} repositories", grouped.size());
        return grouped;
    }

    private CSVParser parse() throws IOException {
        ClassPathResource resource = new ClassPathResource(CSV_CLASSPATH_PATH);
        Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();

        return format.parse(reader);
    }
}
