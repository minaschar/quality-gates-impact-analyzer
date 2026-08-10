import { apiClient, unwrap } from '@/api/client';
import type { ApiResponse, ImpactAnalysisResponse, ImpactAnalysisSummaryDto, RepositoryDetectionResult } from '@/types';

/** POST /impact-analysis -- runs (or returns cached) before/after quality comparison. */
export function runImpactAnalysis(owner: string, repo: string, forceNewAnalysis = false) {
  return unwrap(
    apiClient.post<ApiResponse<ImpactAnalysisResponse>>(
      '/impact-analysis',
      {},
      { params: { owner, repo, forceNewAnalysis } }
    )
  );
}

/**
 * POST /impact-analysis/refresh -- forces a fresh detection, commit history fetch, and
 * quality-metrics re-ingestion for a repository. Does not recompute the before/after
 * comparison itself -- follow up with runImpactAnalysis(owner, repo, true) for that,
 * as a deliberate second step.
 */
export function refreshRepositoryData(owner: string, repo: string) {
  return unwrap(
    apiClient.post<ApiResponse<RepositoryDetectionResult>>(
      '/impact-analysis/refresh',
      {},
      { params: { owner, repo } }
    )
  );
}

/** GET /impact-analysis/{owner}/{repo} -- 404s if nothing has been computed yet. */
export function getImpactAnalysis(owner: string, repo: string) {
  return unwrap(apiClient.get<ApiResponse<ImpactAnalysisResponse>>(`/impact-analysis/${owner}/${repo}`));
}

/** GET /impact-analysis -- summary rows for every repo with a computed impact analysis. */
export function listImpactAnalyses() {
  return unwrap(apiClient.get<ApiResponse<ImpactAnalysisSummaryDto[]>>('/impact-analysis'));
}
