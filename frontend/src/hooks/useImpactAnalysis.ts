import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getImpactAnalysis, listImpactAnalyses, refreshRepositoryData, runImpactAnalysis } from '@/api/impactAnalysis';
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
 * Forces a fresh detection, commit history fetch, and quality-metrics re-ingestion. Does not
 * recompute the before/after comparison itself -- callers that want a truly end-to-end refresh
 * (detection + commits + metrics + comparison) chain a forceNewAnalysis=true call via
 * useRunImpactAnalysis on success, same as RepositoryDetail's "Re-run Full Analysis" does.
 */
export function useRefreshRepositoryData() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ owner, repo }: { owner: string; repo: string }) => refreshRepositoryData(owner, repo),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: repositoryKeys.all });
      queryClient.setQueryData(repositoryKeys.detail(result.owner, result.repo), result);
      if (!result.hasQualityGate) {
        // The quality gate is gone -- the backend already deleted any stored impact analysis
        // for this repo, so drop it from the cache too rather than leaving stale before/after
        // data visible on the Quality Impact tab.
        queryClient.invalidateQueries({ queryKey: impactAnalysisKeys.list() });
        queryClient.removeQueries({ queryKey: impactAnalysisKeys.detail(result.owner, result.repo) });
        notify.warning(`No quality gate detected for ${result.owner}/${result.repo}`);
      } else {
        notify.success(`Data refreshed for ${result.owner}/${result.repo}`);
      }
    },
    onError: (error) => notify.error(error, 'Data refresh failed'),
  });
}
