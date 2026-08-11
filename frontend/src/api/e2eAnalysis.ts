import { apiClient, unwrap } from '@/api/client';
import type { ApiResponse, ImpactAnalysisResponse } from '@/types';

// This always does a full detection + full commit re-fetch + full metrics re-ingest with no
// cache shortcuts, then recomputes the before/after comparison -- it can genuinely take several
// minutes for a repo with a large commit history, well past apiClient's default 120s timeout
// (which was tuned for the lighter, often-cached calls). A per-request override avoids raising
// the timeout globally, which would just delay failure feedback for calls that are actually stuck.
const E2E_ANALYSIS_TIMEOUT_MS = 600_000;

/**
 * POST /e2e-analysis -- forces a fresh detection, commit history fetch, quality-metrics
 * re-ingestion, and (if a quality gate is found) an immediate recomputation of the before/after
 * comparison. The single call for a fully forced, end-to-end result.
 */
export function runE2EAnalysis(owner: string, repo: string) {
  return unwrap(
    apiClient.post<ApiResponse<ImpactAnalysisResponse>>(
      '/e2e-analysis',
      {},
      { params: { owner, repo }, timeout: E2E_ANALYSIS_TIMEOUT_MS }
    )
  );
}

/**
 * DELETE /e2e-analysis -- deletes every stored trace of a repository: detection, commit
 * history, quality metrics, and impact analysis. No orphaned data left in any table. Returns
 * 404 if no data exists for this repository at all.
 */
export function deleteAllRepositoryData(owner: string, repo: string) {
  return unwrap(apiClient.delete<ApiResponse<void>>('/e2e-analysis', { params: { owner, repo } }));
}
