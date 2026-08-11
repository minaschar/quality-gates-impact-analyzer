import { apiClient, unwrap } from '@/api/client';
import type { ApiResponse, QualityGateDetectionRequest, RepositoryDetectionResult } from '@/types';

/** Detects (or returns cached) quality gate info for a repo. Maps to POST /quality-gate/detect. */
export function detectQualityGate(repositoryUrl: string, forceNewDetection = false) {
  const body: QualityGateDetectionRequest = { repositoryUrl };
  return unwrap(
    apiClient.post<ApiResponse<RepositoryDetectionResult>>('/quality-gate/detect', body, {
      params: { forceNewDetection },
    })
  );
}

/** GET /quality-gate/{owner}/{repo} -- cached detection only, 404s if none exists. */
export function getQualityGateDetection(owner: string, repo: string) {
  return unwrap(apiClient.get<ApiResponse<RepositoryDetectionResult>>(`/quality-gate/${owner}/${repo}`));
}

/** GET /quality-gate -- all analyzed repositories, optionally filtered to those with a QG. */
export function listRepositories(hasQualityGate?: boolean) {
  return unwrap(
    apiClient.get<ApiResponse<RepositoryDetectionResult[]>>('/quality-gate', {
      params: hasQualityGate !== undefined ? { hasQualityGate } : undefined,
    })
  );
}

export interface RateLimitInfo {
  remaining: number;
  limit: number;
  message: string;
}

/** GET /rate-limit -- plain JSON, not wrapped in the ApiResponse envelope. */
export async function getRateLimit(): Promise<RateLimitInfo> {
  const { data } = await apiClient.get<RateLimitInfo>('/rate-limit');
  return data;
}

export interface HealthInfo {
  status: string;
  version: string;
  database: string;
}

/** GET /health -- plain JSON, not wrapped in the ApiResponse envelope. */
export async function getHealth(): Promise<HealthInfo> {
  const { data } = await apiClient.get<HealthInfo>('/health');
  return data;
}
