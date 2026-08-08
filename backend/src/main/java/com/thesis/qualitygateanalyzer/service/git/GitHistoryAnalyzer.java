package com.thesis.qualitygateanalyzer.service.git;

import com.thesis.qualitygateanalyzer.domain.qualitygate.QualityGateDetection;
import com.thesis.qualitygateanalyzer.domain.qualitygate.QualityGateHistoryDetection;

import java.util.List;

/**
 * Analyzes git history to determine when quality gate tools were introduced
 * in a repository, using the GitHub GraphQL Blame API.
 */
public interface GitHistoryAnalyzer {

    /**
     * Analyze when quality gate tools were introduced in the repository.
     */
    QualityGateHistoryDetection analyzeHistory(String owner, String repo, List<QualityGateDetection> detections);
}
