package com.thesis.qualitygateanalyzer.service.qualitygate;

import com.thesis.qualitygateanalyzer.constant.QualityGateDetectionConstants;
import com.thesis.qualitygateanalyzer.domain.enums.EnforcementStatus;
import com.thesis.qualitygateanalyzer.domain.enums.PROutcome;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.*;
import com.thesis.qualitygateanalyzer.exception.RepositoryNotFoundException;
import com.thesis.qualitygateanalyzer.service.detection.ConfigurationParser;
import com.thesis.qualitygateanalyzer.service.detection.ConfigurationParser.WorkflowParseResult;
import com.thesis.qualitygateanalyzer.service.git.GitHistoryAnalyzer;
import com.thesis.qualitygateanalyzer.service.github.CheckRunMatcher;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Default {@link QualityGateDetectionService} implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityGateDetectionServiceImpl implements QualityGateDetectionService {

    private final GitHubApiClient github;
    private final ConfigurationParser configParser;
    private final CheckRunMatcher checkRunMatcher;
    private final GitHistoryAnalyzer gitHistoryAnalyzer;

    private static final Pattern GITHUB_URL = Pattern.compile(
            "(?:https?://)?(?:www\\.)?github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$"
    );

    private static final List<String> BUILD_CONFIG_FILES = List.of(
            "pom.xml", "build.gradle", "build.gradle.kts",
            "package.json", "pyproject.toml", "setup.cfg",
            "go.mod", "Cargo.toml", "Gemfile", "composer.json"
    );

    private static final List<String> QG_CONFIG_FILES = List.of(
            "sonar-project.properties", ".sonarcloud.properties",
            "codecov.yml", ".codecov.yml", "codecov.yaml",
            ".codacy.yml", ".codacy.yaml", ".codeclimate.yml",
            "checkstyle.xml", ".eslintrc", ".eslintrc.js", ".eslintrc.json",
            ".golangci.yml", ".golangci.yaml", ".rubocop.yml",
            "phpstan.neon", "psalm.xml", "detekt.yml"
    );

    @Override
    public RepositoryDetectionResult detect(String repoUrl) {
        Instant startTime = Instant.now();
        github.resetCallCounter();

        String[] ownerRepo = parseUrl(repoUrl);
        String owner = ownerRepo[0];
        String repo = ownerRepo[1];

        log.info("Starting detection for {}/{}", owner, repo);

        Map<String, Object> repoInfo = github.getRepository(owner, repo)
                .orElseThrow(() -> new RepositoryNotFoundException("Repository not found: " + owner + "/" + repo));

        String description = (String) repoInfo.get("description");
        String language = (String) repoInfo.get("language");
        String defaultBranch = (String) repoInfo.getOrDefault("default_branch", "main");
        int stars = ((Number) repoInfo.getOrDefault("stargazers_count", 0)).intValue();
        int forks = ((Number) repoInfo.getOrDefault("forks_count", 0)).intValue();

        Phase1Result phase1 = executePhase1(owner, repo);

        log.info("Phase 1 - {} quality gate(s) detected: {}",
                phase1.thesisRelevantDetections.size(),
                phase1.thesisRelevantDetections.stream()
                        .map(d -> d.getTool().getDisplayName())
                        .distinct()
                        .collect(Collectors.joining(", ")));

        if (phase1.thesisRelevantDetections.isEmpty()) {
            log.info("No relevant quality gates found for {}/{}. Skipping enforcement detection.", owner, repo);
            return buildResult(owner, repo, repoUrl, description, language, defaultBranch,
                    stars, forks, phase1, null, null, null, startTime);
        }

        // Branch protection API calls are skipped to avoid permission issues on public repositories.
        // Enforcement is determined through behavioral detection of PR outcomes.
        Phase15Result phase15 = createDefaultPhase15(phase1.thesisRelevantDetections);

        Phase2Result phase2 = executePhase2(owner, repo, phase1.qgWorkflows);

        log.info("Phase 2 - {} workflow runs checked, {} PR-related, {} PRs with failures",
                phase2.allRuns.size(), phase2.prRelatedRuns.size(), phase2.prsWithFailures.size());

        Phase25Result phase25 = executePhase25(owner, repo, phase2.prsWithFailures,
                phase1.thesisRelevantDetections, phase15);

        log.info("Phase 2.5 - {} PRs with verified quality gate failures ({} from external checks)",
                phase25.verifiedQGFailures.size(), phase25.externalCheckFailuresFound);

        Phase3Result phase3 = executePhase3(owner, repo, phase2, phase25, phase15);

        log.info("Phase 3 - PR outcomes: fixed_then_merged={}, blocked={}, merged_with_failure={}",
                phase3.fixedThenMerged, phase3.blocked, phase3.mergedWithFailure);

        EnforcementDetectionResult enforcement = executePhase4(phase2, phase3, phase1, phase15);

        log.info("Phase 4 - enforcement status: {}, score: {}",
                enforcement.getStatus().getDisplayName(),
                enforcement.getScore() != null ? String.format("%.1f%%", enforcement.getScore() * 100) : "N/A");

        QualityGateHistoryDetection historyDetection = executePhase5(
                owner, repo, phase1.thesisRelevantDetections);

        if (historyDetection != null && historyDetection.getMetadata().isSuccess()) {
            log.info("Phase 5 - history detection complete: {} tools scanned in {}ms",
                    historyDetection.getMetadata().getToolsScanned(),
                    historyDetection.getMetadata().getTotalDurationMs());
        } else {
            log.warn("Phase 5 - history detection skipped or failed");
        }

        log.info("Detection complete for {}/{} - {} API calls used", owner, repo, github.getApiCallCount());

        return buildResult(owner, repo, repoUrl, description, language, defaultBranch,
                stars, forks, phase1, phase15, enforcement, historyDetection, startTime);
    }

    // Phase 5: quality gate history detection

    /**
     * Execute phase 5 - analyze git history to find when quality gate tools were introduced.
     */
    private QualityGateHistoryDetection executePhase5(
            String owner,
            String repo,
            List<QualityGateDetection> detections) {

        if (detections == null || detections.isEmpty()) {
            return null;
        }

        return gitHistoryAnalyzer.analyzeHistory(owner, repo, detections);
    }

    // Phase 1: static configuration detection

    private Phase1Result executePhase1(String owner, String repo) {
        List<QualityGateDetection> allDetections = new ArrayList<>();
        List<QualityGateWorkflow> qgWorkflows = new ArrayList<>();
        Map<String, List<String>> workflowBuildCommands = new HashMap<>();

        // 1A: Scan workflow files
        log.info("Scanning workflow files...");
        List<String> workflowFiles = github.listWorkflowFiles(owner, repo);

        for (String wfPath : workflowFiles) {
            Optional<String> content = github.getFileContent(owner, repo, wfPath);
            if (content.isEmpty()) continue;

            WorkflowParseResult result = configParser.parseWorkflow(wfPath, content.get());

            if (!result.detections().isEmpty()) {
                allDetections.addAll(result.detections());

                List<QualityGateTool> tools = result.detections().stream()
                        .map(QualityGateDetection::getTool)
                        .distinct()
                        .toList();

                qgWorkflows.add(QualityGateWorkflow.builder()
                        .workflowFile(wfPath)
                        .workflowName(extractWorkflowName(content.get()))
                        .tools(tools)
                        .triggersOnPR(result.triggersOnPR())
                        .buildCommands(result.buildCommandsFound())
                        .build());
            }

            // Track build commands for linking with build configs
            if (!result.buildCommandsFound().isEmpty()) {
                workflowBuildCommands.put(wfPath, result.buildCommandsFound());
            }
        }

        // 1B: Scan build tool configs
        log.info("Scanning build tool configs...");
        for (String configPath : BUILD_CONFIG_FILES) {
            Optional<String> content = github.getFileContent(owner, repo, configPath);
            if (content.isEmpty()) continue;

            // Find associated workflow (smart linking)
            String associatedWorkflow = findAssociatedWorkflow(configPath, workflowBuildCommands);

            List<QualityGateDetection> detections = configParser.parseBuildConfig(
                    configPath, content.get(), associatedWorkflow);

            // If we found QG in build config but it's linked to a workflow,
            // add that workflow to qgWorkflows if not already there
            if (!detections.isEmpty() && associatedWorkflow != null) {
                boolean workflowAlreadyTracked = qgWorkflows.stream()
                        .anyMatch(w -> w.getWorkflowFile().equals(associatedWorkflow));

                if (!workflowAlreadyTracked) {
                    List<QualityGateTool> tools = detections.stream()
                            .map(QualityGateDetection::getTool)
                            .distinct()
                            .toList();

                    qgWorkflows.add(QualityGateWorkflow.builder()
                            .workflowFile(associatedWorkflow)
                            .workflowName("(via " + configPath + ")")
                            .tools(tools)
                            .triggersOnPR(true) // Assume it does if running build
                            .buildCommands(workflowBuildCommands.getOrDefault(associatedWorkflow, List.of()))
                            .build());
                }
            }

            allDetections.addAll(detections);
        }

        // 1C: Scan dedicated QG config files
        log.info("Scanning dedicated config files...");
        for (String configPath : QG_CONFIG_FILES) {
            Optional<String> content = github.getFileContent(owner, repo, configPath);
            if (content.isEmpty()) continue;

            configParser.parseConfigFile(configPath, content.get())
                    .ifPresent(allDetections::add);
        }

        // Deduplicate and filter
        List<QualityGateDetection> unique = deduplicateDetections(allDetections);
        List<QualityGateDetection> thesisRelevant = unique.stream()
                .filter(QualityGateDetection::isRelevantForThesis)
                .toList();

        return new Phase1Result(unique, thesisRelevant, qgWorkflows);
    }

    private record Phase1Result(
            List<QualityGateDetection> allDetections,
            List<QualityGateDetection> thesisRelevantDetections,
            List<QualityGateWorkflow> qgWorkflows
    ) {
    }

    /**
     * Creates a default Phase15Result without making API calls.
     * All detected tools are treated as potentially enforced - actual enforcement
     * is determined through behavioral detection in later phases.
     */
    private Phase15Result createDefaultPhase15(List<QualityGateDetection> detectedTools) {
        Set<QualityGateTool> allTools = detectedTools.stream()
                .map(QualityGateDetection::getTool)
                .collect(Collectors.toSet());

        // Treat all tools as "potentially enforced" - we'll determine enforcement through behavior
        return new Phase15Result(null, allTools, Set.of());
    }

    private record Phase15Result(
            BranchProtection branchProtection,
            Set<QualityGateTool> requiredQGTools,
            Set<QualityGateTool> informationalQGTools
    ) {
    }

    // Phase 2: workflow runs detection

    private Phase2Result executePhase2(String owner, String repo, List<QualityGateWorkflow> qgWorkflows) {
        List<WorkflowRun> allRuns = new ArrayList<>();

        for (QualityGateWorkflow qgWorkflow : qgWorkflows) {
            List<Map<String, Object>> rawRuns = github.getWorkflowRuns(
                    owner, repo, qgWorkflow.getWorkflowFile(), QualityGateDetectionConstants.WORKFLOW_RUNS_LIMIT);

            for (Map<String, Object> raw : rawRuns) {
                allRuns.add(github.parseWorkflowRun(raw, qgWorkflow.getWorkflowFile()));
            }
        }

        // Filter PR-related runs
        List<WorkflowRun> prRelatedRuns = allRuns.stream()
                .filter(WorkflowRun::isForPR)
                .toList();

        // Group by PR and find PRs with failures
        Map<Integer, List<WorkflowRun>> runsByPR = prRelatedRuns.stream()
                .filter(r -> r.getPrNumber() != null)
                .collect(Collectors.groupingBy(WorkflowRun::getPrNumber));

        Map<Integer, PRWorkflowSummary> prsWithFailures = new LinkedHashMap<>();

        for (Map.Entry<Integer, List<WorkflowRun>> entry : runsByPR.entrySet()) {
            int prNumber = entry.getKey();
            List<WorkflowRun> runs = entry.getValue();

            boolean hadFailure = runs.stream().anyMatch(WorkflowRun::isFailed);
            if (!hadFailure) continue;

            // Sort by creation time to find the last run
            List<WorkflowRun> sortedRuns = new ArrayList<>(runs);
            sortedRuns.sort(Comparator.comparing(WorkflowRun::getCreatedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())).reversed());

            WorkflowRun lastRun = sortedRuns.getFirst();

            prsWithFailures.put(prNumber, new PRWorkflowSummary(
                    prNumber,
                    sortedRuns,
                    true,
                    lastRun.isSuccess(),
                    lastRun.getHeadSha()
            ));

            if (prsWithFailures.size() >= QualityGateDetectionConstants.PR_ANALYSIS_LIMIT) break;
        }

        return new Phase2Result(allRuns, prRelatedRuns, prsWithFailures);
    }

    private record Phase2Result(
            List<WorkflowRun> allRuns,
            List<WorkflowRun> prRelatedRuns,
            Map<Integer, PRWorkflowSummary> prsWithFailures
    ) {
    }

    private record PRWorkflowSummary(
            int prNumber,
            List<WorkflowRun> runs,
            boolean hadFailure,
            boolean lastRunPassed,
            String lastCommitSha
    ) {
    }

    // Phase 2.5: check run and commit status verification

    /**
     * Examines both check runs from workflow failures and commit statuses from external
     * GitHub Apps (SonarCloud, Codecov, etc.) to verify quality gate failures on PRs.
     */
    private Phase25Result executePhase25(String owner, String repo,
                                         Map<Integer, PRWorkflowSummary> prsWithFailures,
                                         List<QualityGateDetection> detectedTools,
                                         Phase15Result phase15) {
        Map<Integer, VerifiedQGFailure> verified = new LinkedHashMap<>();
        int externalCheckFailuresFound = 0;

        // PART A: Analyze PRs with known workflow failures
        for (Map.Entry<Integer, PRWorkflowSummary> entry : prsWithFailures.entrySet()) {
            int prNumber = entry.getKey();
            PRWorkflowSummary summary = entry.getValue();

            WorkflowRun failedRun = summary.runs.stream()
                    .filter(WorkflowRun::isFailed)
                    .findFirst()
                    .orElse(null);

            if (failedRun == null) continue;

            ChecksAndStatusesResult combined = getChecksAndStatusesForCommit(
                    owner, repo, failedRun.getHeadSha());

            VerifiedQGFailure failure = analyzeChecksForQGFailure(
                    prNumber, combined, summary.lastRunPassed, phase15);

            if (failure != null) {
                verified.put(prNumber, failure);
                if (failure.hasExternalFailure()) {
                    externalCheckFailuresFound++;
                }
            }
        }

        // PART B: Additionally check recent PRs for external quality gate failures
        // (e.g. from GitHub Apps like SonarCloud, Codecov) that may not appear in workflow runs
        Set<QualityGateTool> externalTools = detectedTools.stream()
                .map(QualityGateDetection::getTool)
                .filter(this::isExternalQGTool)
                .collect(Collectors.toSet());

        if (!externalTools.isEmpty()) {
            log.info("Checking for external quality gate failures: {}",
                    externalTools.stream().map(QualityGateTool::getDisplayName).collect(Collectors.joining(", ")));

            List<Map<String, Object>> recentPRs = github.getRecentClosedPRs(owner, repo, QualityGateDetectionConstants.PR_ANALYSIS_LIMIT);

            for (Map<String, Object> prData : recentPRs) {
                int prNumber = ((Number) prData.get("number")).intValue();

                if (verified.containsKey(prNumber) || prsWithFailures.containsKey(prNumber)) {
                    continue;
                }

                // Use head.sha, not merge_commit_sha: external checks (SonarCloud, Codecov)
                // post their results against the PR head commit, not the merge commit
                String sha = null;
                @SuppressWarnings("unchecked")
                Map<String, Object> head = (Map<String, Object>) prData.get("head");
                if (head != null) {
                    sha = (String) head.get("sha");
                }

                if (sha == null) continue;

                ChecksAndStatusesResult combined = getChecksAndStatusesForCommit(owner, repo, sha);

                int checkRunCount = combined.checkRuns().size();
                int qgCheckRunCount = combined.getQGCheckRuns().size();
                int failedCheckRunCount = combined.getFailedQGCheckRuns().size();
                int statusCount = combined.commitStatuses().size();
                int qgStatusCount = combined.getQGStatuses().size();
                int failedStatusCount = combined.getFailedQGStatuses().size();

                if (failedCheckRunCount > 0 || failedStatusCount > 0) {
                    log.debug("PR #{} - check runs: {}/{} QG-related ({} failed), statuses: {}/{} QG-related ({} failed)",
                            prNumber,
                            qgCheckRunCount, checkRunCount, failedCheckRunCount,
                            qgStatusCount, statusCount, failedStatusCount);
                    for (CheckRun cr : combined.getFailedQGCheckRuns()) {
                        log.debug("Failed check run: name='{}', app='{}', tool={}, conclusion={}",
                                cr.getName(), cr.getAppSlug(), cr.getMatchedTool(), cr.getConclusion());
                    }
                }

                VerifiedQGFailure failure = analyzeChecksForQGFailure(
                        prNumber, combined, false, phase15);

                if (failure != null && !failure.failedTools().isEmpty()) {
                    verified.put(prNumber, failure);
                    if (failure.hasExternalFailure()) {
                        externalCheckFailuresFound++;
                    }
                    log.debug("Quality gate failure detected in PR #{}: {} (external: {})",
                            prNumber,
                            failure.failedTools().stream()
                                    .map(QualityGateTool::getDisplayName)
                                    .collect(Collectors.joining(", ")),
                            failure.hasExternalFailure());
                }

                if (verified.size() >= QualityGateDetectionConstants.PR_ANALYSIS_LIMIT) break;
            }
        }

        return new Phase25Result(verified, externalCheckFailuresFound);
    }

    /**
     * Retrieve both check runs and commit statuses for a given commit SHA.
     */
    private ChecksAndStatusesResult getChecksAndStatusesForCommit(String owner, String repo, String sha) {
        List<Map<String, Object>> rawCheckRuns = github.getCheckRuns(owner, repo, sha);
        List<CheckRun> checkRuns = rawCheckRuns.stream()
                .map(checkRunMatcher::parseAndMatch)
                .toList();

        // Get commit statuses from external apps (SonarCloud, Codecov, etc.)
        List<Map<String, Object>> rawStatuses = github.getCommitStatuses(owner, repo, sha);
        List<CommitStatus> statuses = rawStatuses.stream()
                .map(checkRunMatcher::parseAndMatchStatus)
                .toList();

        return new ChecksAndStatusesResult(checkRuns, statuses);
    }

    private record ChecksAndStatusesResult(
            List<CheckRun> checkRuns,
            List<CommitStatus> commitStatuses
    ) {
        List<CheckRun> getQGCheckRuns() {
            return checkRuns.stream()
                    .filter(CheckRun::isQualityGateRelated)
                    .toList();
        }

        List<CheckRun> getFailedQGCheckRuns() {
            return checkRuns.stream()
                    .filter(CheckRun::isQualityGateRelated)
                    .filter(CheckRun::isFailed)
                    .toList();
        }

        List<CommitStatus> getQGStatuses() {
            return commitStatuses.stream()
                    .filter(CommitStatus::isQualityGateRelated)
                    .toList();
        }

        List<CommitStatus> getFailedQGStatuses() {
            return commitStatuses.stream()
                    .filter(CommitStatus::isQualityGateRelated)
                    .filter(CommitStatus::isFailed)
                    .toList();
        }

        boolean hasAnyQGFailure() {
            return !getFailedQGCheckRuns().isEmpty() || !getFailedQGStatuses().isEmpty();
        }
    }

    /**
     * Analyze check runs and commit statuses to identify quality gate failures on a PR.
     */
    private VerifiedQGFailure analyzeChecksForQGFailure(int prNumber,
                                                        ChecksAndStatusesResult combined,
                                                        boolean lastWorkflowRunPassed,
                                                        Phase15Result phase15) {
        List<CheckRun> failedCheckRuns = combined.getFailedQGCheckRuns();
        List<CommitStatus> failedStatuses = combined.getFailedQGStatuses();

        if (failedCheckRuns.isEmpty() && failedStatuses.isEmpty()) {
            return null;
        }

        Set<QualityGateTool> failedToolsSet = new HashSet<>();
        List<String> failureMessages = new ArrayList<>();
        boolean hasExternalFailure = false;

        // From check runs
        for (CheckRun cr : failedCheckRuns) {
            if (cr.getMatchedTool() != null) {
                failedToolsSet.add(cr.getMatchedTool());
                // Any check run not from github-actions is from an external GitHub App
                String appSlug = cr.getAppSlug();
                if (appSlug != null && !"github-actions".equalsIgnoreCase(appSlug)) {
                    hasExternalFailure = true;
                }
            }
            if (cr.getFailureMessage() != null) {
                failureMessages.add(cr.getFailureMessage());
            }
        }

        // From commit statuses (external apps like SonarCloud via status API)
        for (CommitStatus cs : failedStatuses) {
            if (cs.getMatchedTool() != null) {
                failedToolsSet.add(cs.getMatchedTool());
                hasExternalFailure = true;
            }
            if (cs.getDescription() != null) {
                failureMessages.add(cs.getDescription());
            }
        }

        List<QualityGateTool> failedTools = new ArrayList<>(failedToolsSet);

        // Check if any of the failed tools was a REQUIRED check
        boolean wasRequiredCheck = failedTools.stream()
                .anyMatch(phase15.requiredQGTools::contains);

        return new VerifiedQGFailure(
                prNumber,
                failedTools,
                failureMessages,
                combined.getQGCheckRuns(),
                combined.getQGStatuses(),
                lastWorkflowRunPassed,
                wasRequiredCheck,
                hasExternalFailure
        );
    }

    /**
     * Check if a tool typically runs as an external GitHub App rather than as a workflow step.
     */
    private boolean isExternalQGTool(QualityGateTool tool) {
        return switch (tool) {
            case SONARCLOUD, SONARQUBE -> true;  // Usually GitHub App
            case CODECOV -> true;                 // Usually GitHub App
            case COVERALLS -> true;               // Usually GitHub App
            case CODACY -> true;                  // Usually GitHub App
            case CODE_CLIMATE -> true;            // Usually GitHub App
            case DEEPSOURCE -> true;              // Usually GitHub App
            default -> false;
        };
    }

    private record Phase25Result(
            Map<Integer, VerifiedQGFailure> verifiedQGFailures,
            int externalCheckFailuresFound
    ) {
    }

    private record VerifiedQGFailure(
            int prNumber,
            List<QualityGateTool> failedTools,
            List<String> failureMessages,
            List<CheckRun> qgCheckRuns,
            List<CommitStatus> qgStatuses,
            boolean lastWorkflowRunPassed,
            boolean wasRequiredCheck,
            boolean hasExternalFailure
    ) {
    }

    // Phase 3: PR outcome detection

    private Phase3Result executePhase3(String owner, String repo,
                                       Phase2Result phase2, Phase25Result phase25,
                                       Phase15Result phase15) {
        List<PRDetectionResult> prResults = new ArrayList<>();
        int fixedThenMerged = 0, blocked = 0, mergedWithFailure = 0, stillOpen = 0;
        int mergedWithInformationalFailure = 0;
        Map<QualityGateTool, int[]> byTool = new HashMap<>(); // [enforced, bypassed, isRequired]

        for (Map.Entry<Integer, VerifiedQGFailure> entry : phase25.verifiedQGFailures.entrySet()) {
            int prNumber = entry.getKey();
            VerifiedQGFailure qgFailure = entry.getValue();

            // wfSummary is null for PRs discovered via external checks only (Part B)
            PRWorkflowSummary wfSummary = phase2.prsWithFailures.get(prNumber);
            boolean hasWorkflowData = wfSummary != null;

            Optional<Map<String, Object>> prOpt = github.getPullRequest(owner, repo, prNumber);
            if (prOpt.isEmpty()) continue;

            Map<String, Object> pr = prOpt.get();
            String state = (String) pr.get("state");
            boolean merged = pr.get("merged_at") != null;

            PROutcome outcome = determineOutcome(
                    merged, state,
                    qgFailure.lastWorkflowRunPassed,
                    qgFailure.wasRequiredCheck
            );

            switch (outcome) {
                case FIXED_THEN_MERGED -> fixedThenMerged++;
                case BLOCKED -> blocked++;
                case MERGED_WITH_FAILURE -> {
                    mergedWithFailure++;
                    // If it was an informational QG, track separately
                    if (!qgFailure.wasRequiredCheck) {
                        mergedWithInformationalFailure++;
                    }
                }
                case STILL_OPEN -> stillOpen++;
            }

            for (QualityGateTool tool : qgFailure.failedTools) {
                boolean isRequired = phase15.requiredQGTools.contains(tool);
                byTool.computeIfAbsent(tool, t -> new int[]{0, 0, isRequired ? 1 : 0});
                if (outcome.getEnforcementWorked() == Boolean.TRUE) {
                    byTool.get(tool)[0]++;
                } else if (outcome.getEnforcementWorked() == Boolean.FALSE) {
                    byTool.get(tool)[1]++;
                }
            }

            String prUrl = String.format("https://github.com/%s/%s/pull/%d", owner, repo, prNumber);

            prResults.add(PRDetectionResult.builder()
                    .prNumber(prNumber)
                    .prTitle((String) pr.get("title"))
                    .prUrl(prUrl)
                    .state(state)
                    .merged(merged)
                    .createdAt(parseInstant((String) pr.get("created_at")))
                    .closedAt(parseInstant((String) pr.get("closed_at")))
                    .mergedAt(parseInstant((String) pr.get("merged_at")))
                    .workflowRuns(hasWorkflowData ? wfSummary.runs : List.of())
                    .hadWorkflowFailure(hasWorkflowData)
                    .lastWorkflowRunPassed(hasWorkflowData && wfSummary.lastRunPassed)
                    .qualityGateCheckRuns(qgFailure.qgCheckRuns)
                    .hadVerifiedQGFailure(true)
                    .lastQGCheckPassed(qgFailure.lastWorkflowRunPassed)
                    .failedQGTools(qgFailure.failedTools)
                    .failureMessages(qgFailure.failureMessages)
                    .qgWasRequiredCheck(qgFailure.wasRequiredCheck)
                    .outcome(outcome)
                    .outcomeReason(buildOutcomeReason(outcome, qgFailure.failedTools, qgFailure.wasRequiredCheck))
                    .build());
        }

        return new Phase3Result(prResults, fixedThenMerged, blocked, mergedWithFailure,
                stillOpen, mergedWithInformationalFailure, byTool);
    }

    private record Phase3Result(
            List<PRDetectionResult> prResults,
            int fixedThenMerged,
            int blocked,
            int mergedWithFailure,
            int stillOpen,
            int mergedWithInformationalFailure,
            Map<QualityGateTool, int[]> byTool
    ) {
    }

    // Phase 4: enforcement scoring

    private EnforcementDetectionResult executePhase4(Phase2Result phase2, Phase3Result phase3,
                                                    Phase1Result phase1, Phase15Result phase15) {
        // For scoring, we only count REQUIRED QG failures as bypass.
        // Informational QG failures that were merged are expected behavior.
        int enforced = phase3.fixedThenMerged + phase3.blocked;
        int bypassed = phase3.mergedWithFailure - phase3.mergedWithInformationalFailure;
        int total = enforced + bypassed;

        Double score = total > 0 ? (double) enforced / total : null;

        EnforcementStatus status;
        if (phase15.requiredQGTools.isEmpty()) {
            // No QGs are required - they're all informational
            status = EnforcementStatus.QG_NOT_REQUIRED;
        } else if (total == 0) {
            // No PRs with verified QG failures on required checks
            boolean workflowRunsOnPRs = !phase2.prRelatedRuns.isEmpty();
            status = workflowRunsOnPRs ?
                    EnforcementStatus.QG_ACTIVE_NO_FAILURES :
                    EnforcementStatus.QG_NOT_RUNNING_ON_PRS;
        } else {
            status = EnforcementStatus.fromScore(score);
        }

        String confidence = total >= 15 ? "HIGH" : total >= 5 ? "MEDIUM" : "LOW";

        Map<QualityGateTool, EnforcementDetectionResult.ToolStats> toolStats = new HashMap<>();
        for (Map.Entry<QualityGateTool, int[]> entry : phase3.byTool.entrySet()) {
            QualityGateTool tool = entry.getKey();
            int[] counts = entry.getValue();
            boolean isRequired = phase15.requiredQGTools.contains(tool);
            toolStats.put(tool, EnforcementDetectionResult.ToolStats.builder()
                    .tool(tool)
                    .failures(counts[0] + counts[1])
                    .enforced(counts[0])
                    .bypassed(counts[1])
                    .isRequiredCheck(isRequired)
                    .build());
        }

        EnforcementDetectionResult.BranchProtectionInfo bpInfo = null;
        if (phase15.branchProtection != null) {
            bpInfo = EnforcementDetectionResult.BranchProtectionInfo.builder()
                    .defaultBranch(phase15.branchProtection.getBranch())
                    .isProtected(phase15.branchProtection.isProtected())
                    .hasRequiredStatusChecks(phase15.branchProtection.isRequiresStatusChecks())
                    .requiredCheckNames(phase15.branchProtection.getRequiredChecks() != null ?
                            phase15.branchProtection.getRequiredChecks().stream()
                                    .map(BranchProtection.RequiredCheck::getContext)
                                    .toList() : List.of())
                    .requiredQGTools(phase15.requiredQGTools)
                    .detectedButNotRequired(phase15.informationalQGTools)
                    .enforceAdmins(phase15.branchProtection.isEnforceAdmins())
                    .build();
        }

        EnforcementDetectionResult.FallbackInfo fallbackInfo = null;
        if (total == 0) {
            List<String> configuredEnforcement = phase1.thesisRelevantDetections.stream()
                    .flatMap(d -> d.getEvidenceFound().stream())
                    .filter(e -> e.contains("enforcing"))
                    .distinct()
                    .toList();

            fallbackInfo = EnforcementDetectionResult.FallbackInfo.builder()
                    .workflowRunsOnPRs(!phase2.prRelatedRuns.isEmpty())
                    .successfulQGChecksFound((int) phase2.prRelatedRuns.stream()
                            .filter(WorkflowRun::isSuccess).count())
                    .configuredEnforcement(configuredEnforcement)
                    .likelyReason(buildFallbackReason(phase2, phase1, phase15))
                    .build();
        }

        List<PRDetectionResult> samples = phase3.prResults.stream()
                .limit(QualityGateDetectionConstants.SAMPLE_PRS_LIMIT)
                .toList();

        String interpretation = buildInterpretation(status, phase3, total, score, phase15);

        return EnforcementDetectionResult.builder()
                .status(status)
                .score(score)
                .confidence(confidence)
                .totalPRsChecked(phase2.prsWithFailures.size())
                .prsWithQGFailures(phase3.prResults.size())
                .fixedThenMerged(phase3.fixedThenMerged)
                .blocked(phase3.blocked)
                .mergedWithFailure(phase3.mergedWithFailure)
                .stillOpen(phase3.stillOpen)
                .byTool(toolStats)
                .samplePRs(samples)
                .branchProtection(bpInfo)
                .fallbackInfo(fallbackInfo)
                .interpretation(interpretation)
                .build();
    }

    // Helpers

    private String[] parseUrl(String url) {
        Matcher m = GITHUB_URL.matcher(url.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid GitHub URL: " + url);
        }
        return new String[]{m.group(1), m.group(2)};
    }

    private String extractWorkflowName(String content) {
        Pattern p = Pattern.compile("name:\\s*['\"]?([^'\"\\n]+)['\"]?");
        Matcher m = p.matcher(content);
        return m.find() ? m.group(1).trim() : "Unknown";
    }

    private String findAssociatedWorkflow(String configPath, Map<String, List<String>> workflowBuildCommands) {
        String buildTool = null;
        if (configPath.contains("pom.xml") || configPath.contains("gradle")) {
            buildTool = configPath.contains("pom") ? "maven" : "gradle";
        } else if (configPath.contains("package.json")) {
            buildTool = "npm";
        } else if (configPath.contains("pyproject") || configPath.contains("setup.cfg")) {
            buildTool = "python";
        } else if (configPath.contains("Cargo")) {
            buildTool = "cargo";
        } else if (configPath.contains("go.mod")) {
            buildTool = "go";
        }

        if (buildTool == null) return null;

        String finalBuildTool = buildTool;
        return workflowBuildCommands.entrySet().stream()
                .filter(e -> e.getValue().contains(finalBuildTool))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private List<QualityGateDetection> deduplicateDetections(List<QualityGateDetection> detections) {
        Map<QualityGateTool, QualityGateDetection> best = new LinkedHashMap<>();

        for (QualityGateDetection d : detections) {
            QualityGateDetection existing = best.get(d.getTool());
            if (existing == null || d.getConfidenceScore() > existing.getConfidenceScore()) {
                best.put(d.getTool(), d);
            }
        }

        return new ArrayList<>(best.values());
    }

    private PROutcome determineOutcome(boolean merged, String state, boolean lastRunPassed,
                                       boolean wasRequiredCheck) {
        if ("open".equalsIgnoreCase(state)) {
            return PROutcome.STILL_OPEN;
        }

        if (!merged) {
            return PROutcome.BLOCKED;
        }

        // Merged
        if (lastRunPassed) {
            return PROutcome.FIXED_THEN_MERGED;
        }

        // Merged with failure still present
        return PROutcome.MERGED_WITH_FAILURE;
    }

    private String buildOutcomeReason(PROutcome outcome, List<QualityGateTool> tools,
                                      boolean wasRequiredCheck) {
        String toolNames = tools.stream()
                .map(QualityGateTool::getDisplayName)
                .collect(Collectors.joining(", "));

        String checkType = wasRequiredCheck ? " (REQUIRED)" : " (informational)";

        return switch (outcome) {
            case FIXED_THEN_MERGED -> "Developer fixed " + toolNames + " issues, then merged";
            case BLOCKED -> "PR closed without merge after " + toolNames + " failure" + checkType;
            case MERGED_WITH_FAILURE -> "Merged despite " + toolNames + " failure" + checkType;
            case STILL_OPEN -> "Still open with " + toolNames + " failure" + checkType;
            case NO_FAILURE -> "No QG failure";
        };
    }

    private String buildFallbackReason(Phase2Result phase2, Phase1Result phase1, Phase15Result phase15) {
        if (phase15.requiredQGTools.isEmpty()) {
            return "No QG tools are configured as required status checks";
        }

        if (phase2.prRelatedRuns.isEmpty()) {
            return "Quality gate workflows don't appear to run on PRs";
        }

        boolean allPassed = phase2.prRelatedRuns.stream().allMatch(WorkflowRun::isSuccess);
        if (allPassed) {
            return "All workflow runs passed - team may have high quality standards or thresholds may be loose";
        }

        return "Workflow failures found but couldn't verify they were QG-related";
    }

    private String buildInterpretation(EnforcementStatus status, Phase3Result phase3,
                                       int total, Double score, Phase15Result phase15) {
        if (phase15.requiredQGTools.isEmpty()) {
            return "All detected QG tools are informational only (not required for merge). " +
                    "PRs can be merged even when these checks fail.";
        }

        if (total == 0) {
            return status.getDescription();
        }

        String baseInterpretation = String.format(
                "%s: Out of %d PRs with verified QG failures on REQUIRED checks, " +
                        "%d were fixed then merged, %d were blocked, and %d were merged despite failures " +
                        "(%.0f%% enforcement rate)",
                status.getDisplayName(),
                total,
                phase3.fixedThenMerged,
                phase3.blocked,
                phase3.mergedWithFailure - phase3.mergedWithInformationalFailure,
                score * 100
        );

        if (phase3.mergedWithInformationalFailure > 0) {
            baseInterpretation += String.format(
                    ". Additionally, %d PRs were merged with INFORMATIONAL QG failures (expected behavior).",
                    phase3.mergedWithInformationalFailure
            );
        }

        return baseInterpretation;
    }

    private RepositoryDetectionResult buildResult(String owner, String repo, String url,
                                                 String description, String language,
                                                 String defaultBranch, int stars, int forks,
                                                 Phase1Result phase1, Phase15Result phase15,
                                                 EnforcementDetectionResult enforcement,
                                                 QualityGateHistoryDetection historyDetection,
                                                 Instant startTime) {
        long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();

        String conclusion = buildConclusion(phase1, phase15, enforcement);
        String recommendation = buildRecommendation(phase1, phase15, enforcement);

        return RepositoryDetectionResult.builder()
                .owner(owner)
                .repo(repo)
                .url(url)
                .description(description)
                .primaryLanguage(language)
                .defaultBranch(defaultBranch)
                .stars(stars)
                .forks(forks)
                .allDetections(phase1.allDetections)
                .thesisRelevantDetections(phase1.thesisRelevantDetections)
                .qualityGateWorkflows(phase1.qgWorkflows)
                .hasQualityGate(!phase1.thesisRelevantDetections.isEmpty())
                .branchProtection(phase15 != null ? phase15.branchProtection : null)
                .requiredQGTools(phase15 != null ? phase15.requiredQGTools : Set.of())
                .informationalQGTools(phase15 != null ? phase15.informationalQGTools : Set.of())
                .enforcement(enforcement)
                .qualityGateHistory(historyDetection)
                .conclusion(conclusion)
                .recommendation(recommendation)
                .detectionTimeMs(duration)
                .detectedAt(Instant.now())
                .apiCallsMade(github.getApiCallCount())
                .build();
    }

    private String buildConclusion(Phase1Result phase1, Phase15Result phase15,
                                   EnforcementDetectionResult enforcement) {
        if (phase1.thesisRelevantDetections.isEmpty()) {
            return "No thesis-relevant quality gates detected in this repository.";
        }

        String tools = phase1.thesisRelevantDetections.stream()
                .map(d -> d.getTool().getDisplayName())
                .distinct()
                .collect(Collectors.joining(", "));

        StringBuilder sb = new StringBuilder();
        sb.append("Found quality gate(s): ").append(tools).append(". ");

        if (enforcement != null) {
            sb.append("Enforcement: ").append(enforcement.getStatus().getDisplayName());
            if (enforcement.getScore() != null) {
                sb.append(String.format(" (%.0f%%)", enforcement.getScore() * 100));
            }
            sb.append(".");
        }

        return sb.toString();
    }

    private String buildRecommendation(Phase1Result phase1, Phase15Result phase15,
                                       EnforcementDetectionResult enforcement) {
        if (phase1.thesisRelevantDetections.isEmpty()) {
            return "NOT_SUITABLE - No quality gates found";
        }

        if (phase15 != null && phase15.requiredQGTools.isEmpty()) {
            return "NOT_SUITABLE - QG tools are informational only (not required for merge)";
        }

        if (enforcement == null) {
            return "UNKNOWN - Enforcement detection not performed";
        }

        return switch (enforcement.getStatus()) {
            case STRICTLY_ENFORCED -> "HIGHLY_SUITABLE - Strictly enforced quality gate";
            case MOSTLY_ENFORCED -> "HIGHLY_SUITABLE - Mostly enforced quality gate";
            case QG_ACTIVE_NO_FAILURES -> "POSSIBLY_SUITABLE - QG active but no failures to verify";
            case PARTIALLY_ENFORCED -> "POSSIBLY_SUITABLE - Partially enforced";
            case NOT_ENFORCED -> "NOT_SUITABLE - Quality gate not enforcing";
            case QG_NOT_RUNNING_ON_PRS -> "NOT_SUITABLE - QG doesn't run on PRs";
            case QG_NOT_REQUIRED -> "NOT_SUITABLE - QG not configured as required check";
            default -> "NEEDS_REVIEW - Manual verification recommended";
        };
    }

    private Instant parseInstant(String s) {
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
