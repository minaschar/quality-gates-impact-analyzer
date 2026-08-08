import axios, { type AxiosError } from 'axios';
import { API_BASE_URL } from '@/utils/constants';
import type { ApiResponse } from '@/types';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 120_000, // repo analysis can genuinely take a while (GitHub API calls, git clone)
});

/** Thrown for both transport errors and `{ success: false }` envelopes, so callers only need one catch path. */
export class ApiError extends Error {
  status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/**
 * Unwraps the backend's `ApiResponse<T>` envelope. The backend returns HTTP 200 with
 * `success: false` for validation errors (see QualityGateDetectionController/ImpactAnalysisController),
 * not just non-2xx statuses, so both cases must be normalized into the same thrown error.
 */
export async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  try {
    const { data: envelope } = await promise;
    if (!envelope.success) {
      throw new ApiError(envelope.error ?? envelope.message ?? 'Request failed');
    }
    return envelope.data as T;
  } catch (err) {
    if (err instanceof ApiError) throw err;
    const axiosErr = err as AxiosError<ApiResponse<unknown>>;
    const backendMessage = axiosErr.response?.data?.error ?? axiosErr.response?.data?.message;
    throw new ApiError(backendMessage ?? axiosErr.message ?? 'Network error', axiosErr.response?.status);
  }
}
