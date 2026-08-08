package com.thesis.qualitygateanalyzer.domain.qualitygate;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Records when a quality gate tool was introduced in a specific file.
 */
@Value
@Builder
public class QGFileIntroduction {
    String filePath;                    // The file where tool was found
    String searchPattern;               // What pattern we searched for
    CommitInfo introducedAt;            // When this pattern first appeared
    boolean presentSinceFileCreation;   // True if pattern existed since file was created
    List<CommitInfo> allOccurrences;    // All commits that added/removed this pattern
}
