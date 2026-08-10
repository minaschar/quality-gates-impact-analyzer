package com.thesis.qualitygateanalyzer.service.impactanalysis;

import com.thesis.qualitygateanalyzer.domain.qualitygate.RepositoryDetectionResult;
import com.thesis.qualitygateanalyzer.dto.response.ImpactAnalysisResponse;
import com.thesis.qualitygateanalyzer.dto.response.ImpactAnalysisSummaryDto;

import java.util.List;

/**
 * Determines whether a repository's code quality (from the ingested SonarQube metrics
 * dataset) improved after its quality gate tool(s) were introduced.
 */
public interface ImpactAnalysisService {

    /**
     * Run (or return the cached) impact analysis for a repository.
     * If forceNewAnalysis is true, recomputes and replaces any stored result.
     */
    ImpactAnalysisResponse analyze(String owner, String repo, boolean forceNewAnalysis);

    /**
     * Load a previously computed impact analysis.
     */
    ImpactAnalysisResponse getAnalysis(String owner, String repo);

    /**
     * List all analyzed repositories with summary stats.
     */
    List<ImpactAnalysisSummaryDto> listAll();

    /**
     * Forces a fresh quality-gate detection, commit history fetch, and quality-metrics
     * re-ingestion for a repository -- refreshes every input impact analysis depends on,
     * without recomputing the before/after comparison itself. Callers that want the
     * comparison recomputed from the refreshed data call {@link #analyze} with
     * {@code forceNewAnalysis=true} afterward, as a deliberate second step.
     */
    RepositoryDetectionResult refreshRepositoryData(String owner, String repo);
}
