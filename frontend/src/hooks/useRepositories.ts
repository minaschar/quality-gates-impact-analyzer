import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  deleteQualityGateDetection,
  detectQualityGate,
  getQualityGateDetection,
  listRepositories,
} from '@/api/repositories';
import { notify } from '@/utils/toast';

export const repositoryKeys = {
  all: ['repositories'] as const,
  list: (hasQualityGate?: boolean) => [...repositoryKeys.all, 'list', hasQualityGate ?? 'all'] as const,
  detail: (owner: string, repo: string) => [...repositoryKeys.all, 'detail', owner, repo] as const,
};

export function useRepositories(hasQualityGate?: boolean) {
  return useQuery({
    queryKey: repositoryKeys.list(hasQualityGate),
    queryFn: () => listRepositories(hasQualityGate),
  });
}

export function useRepositoryDetection(owner: string, repo: string, enabled = true) {
  return useQuery({
    queryKey: repositoryKeys.detail(owner, repo),
    queryFn: () => getQualityGateDetection(owner, repo),
    enabled: enabled && Boolean(owner) && Boolean(repo),
    retry: false, // 404 just means "not analyzed yet" -- don't retry a definitive answer
  });
}

export function useDetectQualityGate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ repositoryUrl, forceNewDetection }: { repositoryUrl: string; forceNewDetection?: boolean }) =>
      detectQualityGate(repositoryUrl, forceNewDetection),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: repositoryKeys.all });
      queryClient.setQueryData(repositoryKeys.detail(result.owner, result.repo), result);
      notify.success(`Detection complete for ${result.owner}/${result.repo}`);
    },
    onError: (error) => notify.error(error, 'Detection failed'),
  });
}

export function useDeleteRepository() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ owner, repo }: { owner: string; repo: string }) => deleteQualityGateDetection(owner, repo),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: repositoryKeys.all });
      queryClient.removeQueries({ queryKey: repositoryKeys.detail(variables.owner, variables.repo) });
      notify.success(`Deleted ${variables.owner}/${variables.repo}`);
    },
    onError: (error) => notify.error(error, 'Delete failed'),
  });
}
