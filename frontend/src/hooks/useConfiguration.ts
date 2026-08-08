import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  batchUpdateConfiguration,
  getAllConfiguration,
  getFeatureFlags,
  getLimits,
  refreshConfigurationCache,
  updateConfiguration,
} from '@/api/configuration';
import { notify } from '@/utils/toast';

export const configurationKeys = {
  all: ['configuration'] as const,
  list: () => [...configurationKeys.all, 'list'] as const,
  limits: () => [...configurationKeys.all, 'limits'] as const,
  features: () => [...configurationKeys.all, 'features'] as const,
};

export function useConfiguration() {
  return useQuery({
    queryKey: configurationKeys.list(),
    queryFn: getAllConfiguration,
  });
}

export function useLimits() {
  return useQuery({
    queryKey: configurationKeys.limits(),
    queryFn: getLimits,
  });
}

export function useFeatureFlags() {
  return useQuery({
    queryKey: configurationKeys.features(),
    queryFn: getFeatureFlags,
  });
}

export function useUpdateConfiguration() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) => updateConfiguration(key, value),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: configurationKeys.all });
      notify.success(`Updated ${variables.key}`);
    },
    onError: (error) => notify.error(error, 'Update failed'),
  });
}

export function useBatchUpdateConfiguration() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (updates: Record<string, string>) => batchUpdateConfiguration(updates),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: configurationKeys.all });
      notify.success('Configuration updated');
    },
    onError: (error) => notify.error(error, 'Batch update failed'),
  });
}

export function useRefreshConfigurationCache() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: refreshConfigurationCache,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: configurationKeys.all });
      notify.success('Configuration cache refreshed');
    },
    onError: (error) => notify.error(error, 'Refresh failed'),
  });
}
