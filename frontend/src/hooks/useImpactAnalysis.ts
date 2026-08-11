import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getImpactAnalysis, listImpactAnalyses, runImpactAnalysis } from '@/api/impactAnalysis';
import { deleteAllRepositoryData, runE2EAnalysis } from '@/api/e2eAnalysis';
import { repositoryKeys } from '@/hooks/useRepositories';
import { notify } from '@/utils/toast';

export const impactAnalysisKeys = {
  all: ['impact-analysis'] as const,
  list: () => [...impactAnalysisKeys.all, 'list'] as const,
  detail: (owner: string, repo: string) => [...impactAnalysisKeys.all, 'detail', owner, repo] as const,
};

export function useImpactAnalysisList() {
  return useQuery({
    queryKey: impactAnalysisKeys.list(),
    queryFn: listImpactAnalyses,
  });
}

export function useImpactAnalysis(owner: string, repo: string, enabled = true) {
  return useQuery({
    queryKey: impactAnalysisKeys.detail(owner, repo),
    queryFn: () => getImpactAnalysis(owner, repo),
    enabled: enabled && Boolean(owner) && Boolean(repo),
    retry: false, // 404 means "not computed yet", not a transient failure
  });
}

export function useRunImpactAnalysis() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      owner,
      repo,
      forceNewAnalysis,
    }: {
      owner: string;
      repo: string;
      forceNewAnalysis?: boolean;
    }) => runImpactAnalysis(owner, repo, forceNewAnalysis),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: impactAnalysisKeys.list() });
      queryClient.setQueryData(impactAnalysisKeys.detail(result.owner, result.repo), result);
      if (!result.hasQualityGate) {
        notify.warning(`No quality gate detected for ${result.owner}/${result.repo}`);
      } else {
        notify.success(`Impact analysis complete for ${result.owner}/${result.repo}`);
      }
    },
    onError: (error) => notify.error(error, 'Impact analysis failed'),
  });
}

/**
 * Forces a fresh detection, commit history fetch, quality-metrics re-ingestion, and (if a
 * quality gate is found) the before/after comparison -- the single, fully end-to-end action.
 * The response is an ImpactAnalysisResponse, not a RepositoryDetectionResult, so detection data
 * (stars, forks, quality-gate history, etc.) is invalidated rather than optimistically set --
 * a refetch of GET /quality-gate/{owner}/{repo} picks up what refreshRepositoryData already
 * saved server-side.
 */
export function useRunE2EAnalysis() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ owner, repo }: { owner: string; repo: string }) => runE2EAnalysis(owner, repo),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: repositoryKeys.all });
      queryClient.invalidateQueries({ queryKey: impactAnalysisKeys.list() });
      if (result.hasQualityGate) {
        queryClient.setQueryData(impactAnalysisKeys.detail(result.owner, result.repo), result);
        notify.success(`E2E analysis complete for ${result.owner}/${result.repo}`);
      } else {
        // The backend already deleted any stored impact analysis for this repo -- drop it from
        // the cache too rather than leaving stale before/after data visible on the tab.
        queryClient.removeQueries({ queryKey: impactAnalysisKeys.detail(result.owner, result.repo) });
        notify.warning(`No quality gate detected for ${result.owner}/${result.repo}`);
      }
    },
    onError: (error) => notify.error(error, 'E2E analysis failed'),
  });
}

/**
 * Deletes every stored trace of a repository: detection, commit history, quality metrics, and
 * impact analysis. No orphaned data left in any table -- unlike the narrower
 * DELETE /quality-gate/{owner}/{repo}, which only removed detection data.
 */
export function useDeleteRepository() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ owner, repo }: { owner: string; repo: string }) => deleteAllRepositoryData(owner, repo),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: repositoryKeys.all });
      queryClient.removeQueries({ queryKey: repositoryKeys.detail(variables.owner, variables.repo) });
      queryClient.invalidateQueries({ queryKey: impactAnalysisKeys.list() });
      queryClient.removeQueries({ queryKey: impactAnalysisKeys.detail(variables.owner, variables.repo) });
      notify.success(`Deleted ${variables.owner}/${variables.repo}`);
    },
    onError: (error) => notify.error(error, 'Delete failed'),
  });
}
