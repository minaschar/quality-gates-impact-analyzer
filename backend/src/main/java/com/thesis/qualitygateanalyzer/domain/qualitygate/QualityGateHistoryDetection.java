package com.thesis.qualitygateanalyzer.domain.qualitygate;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Complete history detection result for a repository.
 */
@Value
@Builder
public class QualityGateHistoryDetection {
    // Per-tool introduction history
    List<QGToolIntroduction> toolIntroductions;

    // Overall earliest/latest
    QGToolIntroduction earliestIntroduction;    // First QG tool added to repo
    QGToolIntroduction latestIntroduction;      // Most recent QG tool added

    // Repo context
    CommitInfo repoFirstCommit;                 // First commit of the repository
    int totalRepoCommits;                       // Total number of commits

    // Detection metadata
    HistoryDetectionMetadata metadata;

    @Value
    @Builder
    public static class HistoryDetectionMetadata {
        String method;              // "git-cli"
        String cloneType;           // "blobless"
        String cloneDirectory;      // Temp directory used
        long cloneDurationMs;
        long detectionDurationMs;
        long totalDurationMs;
        int filesScanned;
        int toolsScanned;
        boolean success;
        String errorMessage;        // If success=false
    }
}
