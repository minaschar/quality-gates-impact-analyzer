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

    /**
     * Deletes any stored impact analysis for a repository if it no longer has a detected
     * quality gate; a no-op otherwise (including when nothing was stored to begin with).
     * {@link #analyze} and {@link #refreshRepositoryData} already do this internally when
     * they discover "no quality gate" themselves -- this is exposed for detection-only
     * callers (plain {@code POST /quality-gate/detect}, with or without
     * {@code forceNewDetection}) that never otherwise touch impact analysis at all, so a
     * repo whose quality gate tool gets removed doesn't keep serving a stale, now-inaccurate
     * before/after comparison from before it was removed.
     */
    void clearImpactAnalysisIfNoQualityGate(String owner, String repo, boolean hasQualityGate);

    /**
     * Deletes every stored trace of a repository: quality-gate detection (all versions, plus
     * its cascaded workflows/tool-introductions/enforcement data), commit history, ingested
     * quality metrics, and any computed impact analysis. Unlike {@code DELETE
     * /quality-gate/{owner}/{repo}}, which only removes detection data and can leave the other
     * three behind as orphans, this leaves nothing in any table.
     *
     * @throws com.thesis.qualitygateanalyzer.exception.RepositoryDataNotFoundException if none
     *         of the four data sources have anything stored for this owner/repo
     */
    void deleteAllRepositoryData(String owner, String repo);
}
