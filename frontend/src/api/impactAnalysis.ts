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

// Unlike every other mutation here, this one always does the full detection + full commit
// re-fetch + full metrics re-ingest with no cache shortcuts -- it can genuinely take several
// minutes for a repo with a large commit history, well past apiClient's default 120s timeout
// (which was tuned for the lighter, often-cached calls). A per-request override avoids raising
// the timeout globally, which would just delay failure feedback for calls that are actually stuck.
const REFRESH_DATA_TIMEOUT_MS = 600_000;

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
      { params: { owner, repo }, timeout: REFRESH_DATA_TIMEOUT_MS }
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
