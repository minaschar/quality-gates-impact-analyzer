import type {
  ConfigCategory,
  EnforcementStatus,
  ImpactTrend,
  PROutcome,
  QualityGateCategory,
  QualityGateTool,
} from '@/types';

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080/api/v1';

// Backend enums serialize by Java constant name only -- these display names live purely
// on the frontend and must be kept in sync with QualityGateTool.java's displayName field.
export const TOOL_DISPLAY_NAMES: Record<QualityGateTool, string> = {
  SONARQUBE: 'SonarQube',
  SONARCLOUD: 'SonarCloud',
  CODACY: 'Codacy',
  CODE_CLIMATE: 'Code Climate',
  QODANA: 'Qodana',
  DEEPSOURCE: 'DeepSource',
  CHECKSTYLE: 'Checkstyle',
  PMD: 'PMD',
  SPOTBUGS: 'SpotBugs',
  ESLINT: 'ESLint',
  PRETTIER: 'Prettier',
  KTLINT: 'ktlint',
  DETEKT: 'detekt',
  PYLINT: 'Pylint',
  FLAKE8: 'Flake8',
  RUFF: 'Ruff',
  BLACK: 'Black',
  MYPY: 'MyPy',
  GOLANGCI_LINT: 'GolangCI-Lint',
  RUBOCOP: 'RuboCop',
  PHPCS: 'PHP_CodeSniffer',
  PHPSTAN: 'PHPStan',
  PSALM: 'Psalm',
  CLIPPY: 'Clippy',
  RUSTFMT: 'Rustfmt',
  SWIFTLINT: 'SwiftLint',
  CODECOV: 'Codecov',
  COVERALLS: 'Coveralls',
  JACOCO: 'JaCoCo',
  COBERTURA: 'Cobertura',
  ISTANBUL: 'Istanbul/NYC',
  COVERAGE_PY: 'Coverage.py',
  SNYK: 'Snyk',
  TRIVY: 'Trivy',
  DEPENDABOT: 'Dependabot',
  SEMGREP: 'Semgrep',
};

export const CATEGORY_DISPLAY_NAMES: Record<QualityGateCategory, string> = {
  CODE_QUALITY: 'Code Quality',
  CODE_STYLE: 'Code Style',
  COVERAGE: 'Test Coverage',
  SECURITY: 'Security',
  LICENSE: 'License',
};

export const ENFORCEMENT_STATUS_DISPLAY_NAMES: Record<EnforcementStatus, string> = {
  STRICTLY_ENFORCED: 'Strictly Enforced',
  MOSTLY_ENFORCED: 'Mostly Enforced',
  PARTIALLY_ENFORCED: 'Partially Enforced',
  NOT_ENFORCED: 'Not Enforced',
  QG_ACTIVE_NO_FAILURES: 'Active - No Failures',
  QG_NOT_RUNNING_ON_PRS: 'Not Running on PRs',
  QG_NOT_REQUIRED: 'Not Required',
  CONFIGURED_NOT_VERIFIED: 'Configured - Unverified',
  NO_QUALITY_GATE: 'No Quality Gate',
  INSUFFICIENT_DATA: 'Insufficient Data',
};

export const PR_OUTCOME_DISPLAY_NAMES: Record<PROutcome, string> = {
  FIXED_THEN_MERGED: 'Fixed Then Merged',
  BLOCKED: 'Blocked',
  MERGED_WITH_FAILURE: 'Merged With Failure',
  STILL_OPEN: 'Still Open',
  NO_FAILURE: 'No Failure',
};

export const IMPACT_TREND_DISPLAY_NAMES: Record<ImpactTrend, string> = {
  IMPROVED: 'Improved',
  DEGRADED: 'Degraded',
  UNCHANGED: 'Unchanged',
  INSUFFICIENT_DATA: 'Insufficient Data',
};

export const CONFIG_CATEGORY_DISPLAY_NAMES: Record<ConfigCategory, string> = {
  API: 'API Settings',
  LIMITS: 'Limits',
  FEATURES: 'Feature Flags',
};

export const METRIC_DISPLAY_NAMES: Record<string, string> = {
  bugs: 'Bugs',
  vulnerabilities: 'Vulnerabilities',
  codeSmells: 'Code Smells',
  securityHotspots: 'Security Hotspots',
  coverage: 'Coverage',
  duplicatedLinesDensity: 'Duplicated Lines',
  ncloc: 'Lines of Code',
  complexity: 'Complexity',
  cognitiveComplexity: 'Cognitive Complexity',
  softwareQualityReliabilityIssues: 'Reliability Issues',
  softwareQualityMaintainabilityIssues: 'Maintainability Issues',
  softwareQualitySecurityIssues: 'Security Issues',
};
