import type { ConfigDataType } from '@/types';

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

// Mirrors ConfigurationServiceImpl.validateValue() exactly: blank is only valid for STRING
// (e.g. clearing an optional token back to "use the env-configured fallback"); INTEGER requires
// a positive whole number (every INTEGER config is a limit/threshold/count); BOOLEAN/DOUBLE
// require a genuinely well-formed value for their type.
export function validateConfigValue(value: string, dataType: ConfigDataType): ValidationResult {
  if (dataType === 'STRING') {
    return { valid: true };
  }

  const trimmed = value.trim();
  if (!trimmed) {
    return { valid: false, error: `Value cannot be empty for type ${dataType}.` };
  }

  switch (dataType) {
    case 'INTEGER': {
      // Every current INTEGER config is a limit/threshold/count -- zero or negative isn't a
      // meaningful value for any of them, so require strictly positive, not just "a whole number".
      if (!/^-?\d+$/.test(trimmed)) {
        return { valid: false, error: 'Enter a whole number.' };
      }
      return Number(trimmed) > 0 ? { valid: true } : { valid: false, error: 'Must be a positive whole number.' };
    }
    case 'BOOLEAN':
      return trimmed.toLowerCase() === 'true' || trimmed.toLowerCase() === 'false'
        ? { valid: true }
        : { valid: false, error: 'Must be true or false.' };
    case 'DOUBLE':
      return Number.isNaN(Number(trimmed)) ? { valid: false, error: 'Enter a valid number.' } : { valid: true };
  }
}
