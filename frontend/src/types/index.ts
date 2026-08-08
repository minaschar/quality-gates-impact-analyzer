// Types mirror the backend's actual DTOs/domain objects (com.thesis.qualitygateanalyzer),
// not an idealized API -- field names and enum values must match Jackson's default
// serialization (enums serialize as their Java constant name, e.g. "SONARQUBE").

// ── Enums ──────────────────────────────────────────────────────────────────

export type QualityGateTool =
  | 'SONARQUBE' | 'SONARCLOUD' | 'CODACY' | 'CODE_CLIMATE' | 'QODANA' | 'DEEPSOURCE'
  | 'CHECKSTYLE' | 'PMD' | 'SPOTBUGS' | 'ESLINT' | 'PRETTIER' | 'KTLINT' | 'DETEKT'
  | 'PYLINT' | 'FLAKE8' | 'RUFF' | 'BLACK' | 'MYPY' | 'GOLANGCI_LINT' | 'RUBOCOP'
  | 'PHPCS' | 'PHPSTAN' | 'PSALM' | 'CLIPPY' | 'RUSTFMT' | 'SWIFTLINT'
  | 'CODECOV' | 'COVERALLS' | 'JACOCO' | 'COBERTURA' | 'ISTANBUL' | 'COVERAGE_PY'
  | 'SNYK' | 'TRIVY' | 'DEPENDABOT' | 'SEMGREP';

export type QualityGateCategory = 'CODE_QUALITY' | 'CODE_STYLE' | 'COVERAGE' | 'SECURITY' | 'LICENSE';

export type EnforcementStatus =
  | 'STRICTLY_ENFORCED' | 'MOSTLY_ENFORCED' | 'PARTIALLY_ENFORCED' | 'NOT_ENFORCED'
  | 'QG_ACTIVE_NO_FAILURES' | 'QG_NOT_RUNNING_ON_PRS' | 'QG_NOT_REQUIRED' | 'CONFIGURED_NOT_VERIFIED'
  | 'NO_QUALITY_GATE' | 'INSUFFICIENT_DATA';

export type PROutcome = 'FIXED_THEN_MERGED' | 'BLOCKED' | 'MERGED_WITH_FAILURE' | 'STILL_OPEN' | 'NO_FAILURE';

export type ImpactTrend = 'IMPROVED' | 'DEGRADED' | 'UNCHANGED' | 'INSUFFICIENT_DATA';

// ── Generic API envelope ──────────────────────────────────────────────────

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  error?: string;
  timestamp?: string;
  correlationId?: string;
  errors?: { field?: string; message?: string }[];
}

// ── Quality gate detection domain ─────────────────────────────────────────

export interface CommitInfo {
  sha: string;
  shortSha: string;
  author: string;
  date: string;
  message: string;
}

export interface QGFileIntroduction {
  filePath: string;
  searchPattern: string;
  introducedAt: CommitInfo;
  presentSinceFileCreation: boolean;
  allOccurrences: CommitInfo[];
}

export interface QGToolIntroduction {
  tool: QualityGateTool;
  category: QualityGateCategory;
  configIntroductions: QGFileIntroduction[];
  ciIntroductions: QGFileIntroduction[];
  effectiveIntroductionCommit?: CommitInfo;
  effectiveDate?: string;
  presentSinceRepoCreation: boolean;
  introductionSummary: string;
}

export interface QualityGateHistoryDetection {
  toolIntroductions: QGToolIntroduction[];
  earliestIntroduction?: QGToolIntroduction;
  latestIntroduction?: QGToolIntroduction;
  repoFirstCommit?: CommitInfo;
  totalRepoCommits: number;
  metadata?: {
    method: string;
    cloneType: string;
    cloneDirectory: string;
    cloneDurationMs: number;
    detectionDurationMs: number;
    totalDurationMs: number;
    filesScanned: number;
    toolsScanned: number;
    success: boolean;
    errorMessage?: string;
  };
}

export type QualityGateSourceType = 'WORKFLOW_ACTION' | 'WORKFLOW_COMMAND' | 'BUILD_TOOL' | 'CONFIG_FILE';

export interface QualityGateDetection {
  tool: QualityGateTool;
  category: QualityGateCategory;
  sourceFile: string;
  sourceType: QualityGateSourceType;
  evidenceFound: string[];
  confidenceScore: number;
  triggersOnPR?: boolean;
  associatedWorkflow?: string;
}

export interface QualityGateWorkflow {
  workflowFile: string;
  workflowName: string;
  tools: QualityGateTool[];
  triggersOnPR: boolean;
  buildCommands: string[];
}

export interface BranchProtectionRequiredCheck {
  context: string;
  appId?: number;
  appSlug?: string;
  matchedTool?: QualityGateTool;
}

export interface BranchProtection {
  branch: string;
  isProtected: boolean;
  requiresStatusChecks: boolean;
  strictStatusChecks: boolean;
  requiredChecks: BranchProtectionRequiredCheck[];
  enforceAdmins: boolean;
  allowForcePushes: boolean;
  allowDeletions: boolean;
}

export interface WorkflowRun {
  id: number;
  workflowFile: string;
  workflowName: string;
  headSha: string;
  conclusion?: string;
  status: string;
  event: string;
  prNumber?: number;
  createdAt: string;
  htmlUrl: string;
}

export interface CheckRun {
  id: number;
  name: string;
  appSlug?: string;
  conclusion?: string;
  status: string;
  outputTitle?: string;
  outputSummary?: string;
  headSha: string;
  completedAt?: string;
  matchedTool?: QualityGateTool;
  matchConfidence: number;
}

export interface CommitStatus {
  id?: number;
  state: string;
  context: string;
  description?: string;
  targetUrl?: string;
  createdAt?: string;
  updatedAt?: string;
  matchedTool?: QualityGateTool;
  matchConfidence: number;
}

export interface PRDetectionResult {
  prNumber: number;
  prTitle: string;
  prUrl: string;
  state: string;
  merged: boolean;
  createdAt: string;
  closedAt?: string;
  mergedAt?: string;
  workflowRuns: WorkflowRun[];
  hadWorkflowFailure: boolean;
  lastWorkflowRunPassed: boolean;
  qualityGateCheckRuns: CheckRun[];
  hadVerifiedQGFailure: boolean;
  lastQGCheckPassed: boolean;
  failedQGTools: QualityGateTool[];
  failureMessages: string[];
  qgWasRequiredCheck: boolean;
  outcome: PROutcome;
  outcomeReason: string;
}

export interface EnforcementToolStats {
  tool: QualityGateTool;
  failures: number;
  enforced: number;
  bypassed: number;
  isRequiredCheck: boolean;
  enforcementRate?: number;
}

export interface EnforcementBranchProtectionInfo {
  defaultBranch: string;
  isProtected: boolean;
  hasRequiredStatusChecks: boolean;
  requiredCheckNames: string[];
  requiredQGTools: QualityGateTool[];
  detectedButNotRequired: QualityGateTool[];
  enforceAdmins: boolean;
}

export interface EnforcementFallbackInfo {
  workflowRunsOnPRs: boolean;
  successfulQGChecksFound: number;
  configuredEnforcement: string[];
  likelyReason?: string;
}

export interface EnforcementDetectionResult {
  status: EnforcementStatus;
  score?: number;
  confidence: 'HIGH' | 'MEDIUM' | 'LOW';
  totalPRsChecked: number;
  prsWithQGFailures: number;
  fixedThenMerged: number;
  blocked: number;
  mergedWithFailure: number;
  stillOpen: number;
  byTool: Partial<Record<QualityGateTool, EnforcementToolStats>>;
  samplePRs: PRDetectionResult[];
  branchProtection?: EnforcementBranchProtectionInfo;
  fallbackInfo?: EnforcementFallbackInfo;
  interpretation: string;
}

export interface RepositoryDetectionResult {
  owner: string;
  repo: string;
  url: string;
  description?: string;
  primaryLanguage?: string;
  defaultBranch?: string;
  stars: number;
  forks: number;

  allDetections: QualityGateDetection[];
  thesisRelevantDetections: QualityGateDetection[];
  qualityGateWorkflows: QualityGateWorkflow[];
  hasQualityGate: boolean;

  branchProtection?: BranchProtection;
  requiredQGTools: QualityGateTool[];
  informationalQGTools: QualityGateTool[];

  enforcement?: EnforcementDetectionResult;

  conclusion?: string;
  recommendation?: string;

  detectionTimeMs: number;
  detectedAt: string;
  apiCallsMade: number;

  qualityGateHistory?: QualityGateHistoryDetection;
}

// ── Impact analysis DTOs ───────────────────────────────────────────────────

export interface MetricComparisonDto {
  avgBefore?: number;
  avgAfter?: number;
  medianBefore?: number;
  medianAfter?: number;
  deltaAbsolute?: number;
  deltaPercent?: number;
  trend?: ImpactTrend;
}

export interface ToolComparisonDto {
  tool: QualityGateTool;
  toolCategory: QualityGateCategory;
  introducedAt: string;
  introducedCommitSha?: string;
  samplesBefore: number;
  samplesAfter: number;
  improvementScore?: number;
  trend: ImpactTrend;
  metrics: Record<string, MetricComparisonDto>;
}

export interface TimelinePointDto {
  tool: QualityGateTool;
  classification: 'BEFORE' | 'AFTER';
  pullRequestNumber?: number;
  commitSha?: string;
  commitDate: string;
  bugs?: number;
  vulnerabilities?: number;
  codeSmells?: number;
  securityHotspots?: number;
  coverage?: number;
  duplicatedLinesDensity?: number;
  ncloc?: number;
  complexity?: number;
  cognitiveComplexity?: number;
  softwareQualityReliabilityIssues?: number;
  softwareQualityMaintainabilityIssues?: number;
  softwareQualitySecurityIssues?: number;
}

export interface ImpactAnalysisResponse {
  owner: string;
  repo: string;
  repositoryUrl?: string;
  hasQualityGate: boolean;
  toolsAnalyzed: number;
  overallImprovementScore?: number;
  overallTrend: ImpactTrend;
  dateRangeStart?: string;
  dateRangeEnd?: string;
  computedAt?: string;
  cached: boolean;
  comparisons: ToolComparisonDto[];
  timeline: TimelinePointDto[];
}

export interface ImpactAnalysisSummaryDto {
  owner: string;
  repo: string;
  repositoryUrl?: string;
  toolsAnalyzed: number;
  overallImprovementScore?: number;
  overallTrend: ImpactTrend;
  computedAt?: string;
}

// ── Configuration ──────────────────────────────────────────────────────────

export type ConfigDataType = 'STRING' | 'INTEGER' | 'BOOLEAN' | 'DOUBLE';
export type ConfigCategory = 'API' | 'LIMITS' | 'FEATURES';

export interface ConfigurationEntity {
  id?: string;
  configKey: string;
  configValue: string;
  dataType: ConfigDataType;
  category: ConfigCategory;
  description?: string;
  updatedAt?: string;
}

// ── Requests ────────────────────────────────────────────────────────────────

export interface QualityGateDetectionRequest {
  repositoryUrl: string;
}
