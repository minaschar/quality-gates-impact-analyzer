import { ApiError } from '@/api/client';

export type ErrorKind = 'not-found' | 'rate-limited' | 'server-unavailable' | 'validation' | 'network' | 'unknown';

export interface ErrorDisplay {
  kind: ErrorKind;
  title: string;
  message: string;
}

/**
 * Classifies an unknown error (almost always an {@link ApiError}) into a display-ready
 * shape. Status codes come from the backend's ApiResponse envelope / HTTP status; message
 * text is pattern-matched as a fallback since GitHub rate-limit/abuse errors surface through
 * the backend's generic `catch (Exception e)` handlers as plain message strings, not a
 * dedicated status code.
 */
export function classifyError(error: unknown): ErrorDisplay {
  if (!(error instanceof Error)) {
    return { kind: 'unknown', title: 'Something went wrong', message: 'An unexpected error occurred.' };
  }

  const message = error.message || 'An unexpected error occurred.';
  const status = error instanceof ApiError ? error.status : undefined;
  const lower = message.toLowerCase();

  if (status === 404) {
    return { kind: 'not-found', title: 'Not found', message };
  }
  if (status === 429 || lower.includes('rate limit') || lower.includes('403')) {
    return {
      kind: 'rate-limited',
      title: 'GitHub API rate limit exceeded',
      message: 'Too many requests to GitHub right now. Wait a few minutes and try again, or add a GitHub token in Settings for a higher limit.',
    };
  }
  if (status !== undefined && status >= 500) {
    return { kind: 'server-unavailable', title: 'Server unavailable', message: 'The backend is temporarily unavailable. Please try again shortly.' };
  }
  if (status === undefined || lower.includes('network')) {
    return { kind: 'network', title: 'Network error', message: 'Could not reach the server. Check your connection and try again.' };
  }
  if (status === 400) {
    return { kind: 'validation', title: 'Invalid request', message };
  }
  return { kind: 'unknown', title: 'Something went wrong', message };
}
