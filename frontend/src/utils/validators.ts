// Mirrors the backend's GITHUB_URL pattern in QualityGateDetectionController exactly, so a URL
// accepted here is guaranteed to be accepted there too.
const GITHUB_URL_PATTERN = /^(?:https?:\/\/)?(?:www\.)?github\.com\/([^/]+)\/([^/]+?)(?:\.git)?\/?$/i;

export interface ValidationResult {
  valid: boolean;
  error?: string;
}

export function validateGithubUrl(value: string): ValidationResult {
  const trimmed = value.trim();
  if (!trimmed) {
    return { valid: false, error: 'Repository URL is required.' };
  }
  if (!GITHUB_URL_PATTERN.test(trimmed)) {
    return { valid: false, error: 'Enter a valid GitHub URL, e.g. https://github.com/owner/repository' };
  }
  return { valid: true };
}
