package com.thesis.qualitygateanalyzer.service.impactanalysis;

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
}
