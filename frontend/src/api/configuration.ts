import { apiClient, unwrap } from '@/api/client';
import type { ApiResponse, ConfigurationEntity } from '@/types';

/** GET /configuration -- all values, sensitive ones (TOKEN/SECRET) masked by the backend. */
export function getAllConfiguration() {
  return unwrap(apiClient.get<ApiResponse<ConfigurationEntity[]>>('/configuration'));
}

/** PUT /configuration/{key} */
export function updateConfiguration(key: string, value: string) {
  return unwrap(apiClient.put<ApiResponse<ConfigurationEntity>>(`/configuration/${key}`, { value }));
}

/** PUT /configuration/batch */
export function batchUpdateConfiguration(updates: Record<string, string>) {
  return unwrap(apiClient.put<ApiResponse<Record<string, string>>>('/configuration/batch', updates));
}

/** POST /configuration/refresh */
export function refreshConfigurationCache() {
  return unwrap(apiClient.post<ApiResponse<void>>('/configuration/refresh'));
}

export interface LimitsInfo {
  SAMPLE_PRS_LIMIT: number;
  WORKFLOW_RUNS_LIMIT: number;
  PR_ANALYSIS_LIMIT: number;
  CHECK_RUNS_PER_COMMIT: number;
  MAX_BINARY_SEARCH_ITERATIONS: number;
  LINEAR_SEARCH_THRESHOLD: number;
  SAMPLE_PRS_TO_RETURN: number;
}

/** GET /configuration/limits -- plain JSON, not wrapped in the ApiResponse envelope. */
export async function getLimits(): Promise<LimitsInfo> {
  const { data } = await apiClient.get<LimitsInfo>('/configuration/limits');
  return data;
}

export interface FeatureFlagsInfo {
  ENABLE_PR_FALLBACK: boolean;
  ENABLE_HISTORY_ANALYSIS: boolean;
  ENABLE_EXTERNAL_CHECK_DETECTION: boolean;
}

/** GET /configuration/features -- plain JSON, not wrapped in the ApiResponse envelope. */
export async function getFeatureFlags(): Promise<FeatureFlagsInfo> {
  const { data } = await apiClient.get<FeatureFlagsInfo>('/configuration/features');
  return data;
}
