package com.thesis.qualitygateanalyzer.service.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.EnforcementStatus;
import com.thesis.qualitygateanalyzer.domain.enums.PROutcome;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateCategory;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.*;
import com.thesis.qualitygateanalyzer.exception.RepositoryNotFoundException;
import com.thesis.qualitygateanalyzer.service.detection.ConfigurationParser;
import com.thesis.qualitygateanalyzer.service.detection.ConfigurationParser.WorkflowParseResult;
import com.thesis.qualitygateanalyzer.service.git.GitHistoryAnalyzer;
import com.thesis.qualitygateanalyzer.service.github.CheckRunMatcher;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QualityGateDetectionServiceImplTest {

    @Mock
    private GitHubApiClient github;
    @Mock
    private ConfigurationParser configParser;
    @Mock
    private CheckRunMatcher checkRunMatcher;
    @Mock
    private GitHistoryAnalyzer gitHistoryAnalyzer;

    private QualityGateDetectionServiceImpl service;

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";
    private static final String REPO_URL = "https://github.com/octocat/hello-world";
    private static final String WORKFLOW_FILE = ".github/workflows/ci.yml";

    @BeforeEach
    void setUp() {
        service = new QualityGateDetectionServiceImpl(github, configParser, checkRunMatcher, gitHistoryAnalyzer);
    }

    /**
     * Stubs every getFileContent(...) call to empty, so phase 1's file-scan loops find nothing by default.
     */
    private void stubNoFilesByDefault() {
        lenient().when(github.getFileContent(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
    }

    private Map<String, Object> repoInfoMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("description", "A test repo");
        map.put("language", "Java");
        map.put("default_branch", "main");
        map.put("stargazers_count", 42);
        map.put("forks_count", 7);
        return map;
    }

    // ============================================================
    // Pipeline-level tests (public detect() entry point)
    // ============================================================

    @Nested
    class DetectPipeline {

        @Test
        void detect_repositoryNotFound_throwsRepositoryNotFoundException() {
            when(github.getRepository(OWNER, REPO)).thenReturn(Optional.empty());

            RepositoryNotFoundException ex = assertThrows(RepositoryNotFoundException.class,
                    () -> service.detect(REPO_URL));

            assertThat(ex.getMessage()).isEqualTo("Repository not found: " + OWNER + "/" + REPO);
            verify(github).resetCallCounter();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "not-a-url",
                "https://gitlab.com/owner/repo",
                "ftp://github.com/owner/repo",
                ""
        })
        void detect_invalidUrl_throwsIllegalArgumentException(String badUrl) {
            assertThrows(IllegalArgumentException.class, () -> service.detect(badUrl));
            verifyNoInteractions(configParser, checkRunMatcher, gitHistoryAnalyzer);
        }

        @Test
        void detect_noQualityGatesDetected_returnsEarlyWithoutEnforcement() {
            when(github.getRepository(OWNER, REPO)).thenReturn(Optional.of(repoInfoMap()));
            when(github.listWorkflowFiles(OWNER, REPO)).thenReturn(List.of());
            stubNoFilesByDefault();

            RepositoryDetectionResult result = service.detect(REPO_URL);

            assertThat(result.isHasQualityGate()).isFalse();
            assertThat(result.getEnforcement()).isNull();
            assertThat(result.getQualityGateHistory()).isNull();
            assertThat(result.getConclusion()).isEqualTo("No thesis-relevant quality gates detected in this repository.");
            assertThat(result.getRecommendation()).isEqualTo("NOT_SUITABLE - No quality gates found");
            assertThat(result.getOwner()).isEqualTo(OWNER);
            assertThat(result.getRepo()).isEqualTo(REPO);
            assertThat(result.getStars()).isEqualTo(42);
            assertThat(result.getForks()).isEqualTo(7);
            verifyNoInteractions(gitHistoryAnalyzer);
        }

        @Test
        void detect_missingOptionalRepoFields_fallsBackToDefaults() {
            when(github.getRepository(OWNER, REPO)).thenReturn(Optional.of(new HashMap<>()));
            when(github.listWorkflowFiles(OWNER, REPO)).thenReturn(List.of());
            stubNoFilesByDefault();

            RepositoryDetectionResult result = service.detect(REPO_URL);

            assertThat(result.getDefaultBranch()).isEqualTo("main");
            assertThat(result.getStars()).isZero();
            assertThat(result.getForks()).isZero();
        }

        @Test
        void detect_fixedThenMerged_yieldsStrictlyEnforced() {
            QualityGateDetection detection = QualityGateDetection.builder()
                    .tool(QualityGateTool.SONARCLOUD)
                    .category(QualityGateCategory.CODE_QUALITY)
                    .sourceFile(WORKFLOW_FILE)
                    .sourceType(QualityGateDetection.SourceType.WORKFLOW_COMMAND)
                    .evidenceFound(List.of("sonarcloud"))
                    .confidenceScore(0.9)
                    .triggersOnPR(true)
                    .build();

            setUpWorkflowScan(detection, true);

            WorkflowRun failingRun = WorkflowRun.builder()
                    .id(1L).workflowFile(WORKFLOW_FILE).headSha("sha-fail")
                    .conclusion("failure").status("completed").event("pull_request")
                    .prNumber(42).createdAt(Instant.parse("2024-01-01T00:00:00Z")).build();
            WorkflowRun fixedRun = WorkflowRun.builder()
                    .id(2L).workflowFile(WORKFLOW_FILE).headSha("sha-fixed")
                    .conclusion("success").status("completed").event("pull_request")
                    .prNumber(42).createdAt(Instant.parse("2024-01-02T00:00:00Z")).build();

            Map<String, Object> rawRun1 = Map.of("id", 1);
            Map<String, Object> rawRun2 = Map.of("id", 2);
            when(github.getWorkflowRuns(eq(OWNER), eq(REPO), eq(WORKFLOW_FILE), anyInt()))
                    .thenReturn(List.of(rawRun1, rawRun2));
            when(github.parseWorkflowRun(rawRun1, WORKFLOW_FILE)).thenReturn(failingRun);
            when(github.parseWorkflowRun(rawRun2, WORKFLOW_FILE)).thenReturn(fixedRun);

            Map<String, Object> rawCheckRun = Map.of("id", 100);
            when(github.getCheckRuns(OWNER, REPO, "sha-fail")).thenReturn(List.of(rawCheckRun));
            when(github.getCommitStatuses(OWNER, REPO, "sha-fail")).thenReturn(List.of());

            CheckRun failedCheck = CheckRun.builder()
                    .id(100L).name("SonarCloud Code Analysis").appSlug("sonarcloud")
                    .conclusion("failure").status("completed")
                    .matchedTool(QualityGateTool.SONARCLOUD).matchConfidence(0.9).build();
            when(checkRunMatcher.parseAndMatch(rawCheckRun)).thenReturn(failedCheck);

            when(github.getRecentClosedPRs(eq(OWNER), eq(REPO), anyInt())).thenReturn(List.of());

            when(github.getPullRequest(OWNER, REPO, 42)).thenReturn(Optional.of(Map.of(
                    "state", "closed",
                    "merged_at", "2024-01-02T01:00:00Z",
                    "title", "Fix SonarCloud issues",
                    "created_at", "2024-01-01T00:00:00Z",
                    "closed_at", "2024-01-02T01:00:00Z"
            )));

            QualityGateHistoryDetection history = QualityGateHistoryDetection.builder()
                    .totalRepoCommits(10)
                    .metadata(QualityGateHistoryDetection.HistoryDetectionMetadata.builder()
                            .method("git-cli").success(true).toolsScanned(1).totalDurationMs(50).build())
                    .build();
            when(gitHistoryAnalyzer.analyzeHistory(eq(OWNER), eq(REPO), anyList())).thenReturn(history);

            RepositoryDetectionResult result = service.detect(REPO_URL);

            assertThat(result.isHasQualityGate()).isTrue();
            assertThat(result.getRequiredQGTools()).containsExactly(QualityGateTool.SONARCLOUD);
            EnforcementDetectionResult enforcement = result.getEnforcement();
            assertThat(enforcement.getStatus()).isEqualTo(EnforcementStatus.STRICTLY_ENFORCED);
            assertThat(enforcement.getScore()).isEqualTo(1.0);
            assertThat(enforcement.getFixedThenMerged()).isEqualTo(1);
            assertThat(enforcement.getBlocked()).isZero();
            assertThat(enforcement.getMergedWithFailure()).isZero();
            assertThat(enforcement.getSamplePRs()).hasSize(1);
            assertThat(enforcement.getSamplePRs().getFirst().getOutcome()).isEqualTo(PROutcome.FIXED_THEN_MERGED);
            assertThat(result.getQualityGateHistory()).isNotNull();
            assertThat(result.getRecommendation()).isEqualTo("HIGHLY_SUITABLE - Strictly enforced quality gate");
            assertThat(result.getConclusion()).contains("SonarCloud").contains("Strictly Enforced");
        }

        @Test
        void detect_prClosedWithoutMerge_yieldsBlockedOutcome() {
            QualityGateDetection detection = simpleDetection(QualityGateTool.CHECKSTYLE, false);
            setUpWorkflowScan(detection, true);

            WorkflowRun failingRun = WorkflowRun.builder()
                    .id(1L).workflowFile(WORKFLOW_FILE).headSha("sha-fail")
                    .conclusion("failure").status("completed").event("pull_request")
                    .prNumber(7).createdAt(Instant.parse("2024-01-01T00:00:00Z")).build();

            Map<String, Object> rawRun1 = Map.of("id", 1);
            when(github.getWorkflowRuns(eq(OWNER), eq(REPO), eq(WORKFLOW_FILE), anyInt()))
                    .thenReturn(List.of(rawRun1));
            when(github.parseWorkflowRun(rawRun1, WORKFLOW_FILE)).thenReturn(failingRun);

            Map<String, Object> rawCheckRun = Map.of("id", 200);
            when(github.getCheckRuns(OWNER, REPO, "sha-fail")).thenReturn(List.of(rawCheckRun));
            when(github.getCommitStatuses(OWNER, REPO, "sha-fail")).thenReturn(List.of());

            CheckRun failedCheck = CheckRun.builder()
                    .id(200L).name("Checkstyle").appSlug("github-actions")
                    .conclusion("failure").status("completed")
                    .matchedTool(QualityGateTool.CHECKSTYLE).matchConfidence(0.7).build();
            when(checkRunMatcher.parseAndMatch(rawCheckRun)).thenReturn(failedCheck);

            // CHECKSTYLE is not an "external" tool -> Phase 2.5 Part B is skipped entirely.
            when(github.getPullRequest(OWNER, REPO, 7)).thenReturn(Optional.of(Map.of(
                    "state", "closed",
                    "title", "Abandoned PR",
                    "created_at", "2024-01-01T00:00:00Z",
                    "closed_at", "2024-01-03T00:00:00Z"
            )));

            when(gitHistoryAnalyzer.analyzeHistory(eq(OWNER), eq(REPO), anyList())).thenReturn(null);

            RepositoryDetectionResult result = service.detect(REPO_URL);

            verify(github, never()).getRecentClosedPRs(anyString(), anyString(), anyInt());
            EnforcementDetectionResult enforcement = result.getEnforcement();
            assertThat(enforcement.getBlocked()).isEqualTo(1);
            assertThat(enforcement.getStatus()).isEqualTo(EnforcementStatus.STRICTLY_ENFORCED);
            assertThat(enforcement.getSamplePRs().getFirst().getOutcome()).isEqualTo(PROutcome.BLOCKED);
            assertThat(result.getQualityGateHistory()).isNull();
        }

        @Test
        void detect_mergedWhileStillFailing_yieldsMergedWithFailureAndNotEnforced() {
            QualityGateDetection detection = simpleDetection(QualityGateTool.ESLINT, false);
            setUpWorkflowScan(detection, true);

            WorkflowRun failingRun = WorkflowRun.builder()
                    .id(1L).workflowFile(WORKFLOW_FILE).headSha("sha-fail")
                    .conclusion("failure").status("completed").event("pull_request")
                    .prNumber(9).createdAt(Instant.parse("2024-01-01T00:00:00Z")).build();

            Map<String, Object> rawRun1 = Map.of("id", 1);
            when(github.getWorkflowRuns(eq(OWNER), eq(REPO), eq(WORKFLOW_FILE), anyInt()))
                    .thenReturn(List.of(rawRun1));
            when(github.parseWorkflowRun(rawRun1, WORKFLOW_FILE)).thenReturn(failingRun);

            Map<String, Object> rawCheckRun = Map.of("id", 300);
            when(github.getCheckRuns(OWNER, REPO, "sha-fail")).thenReturn(List.of(rawCheckRun));
            when(github.getCommitStatuses(OWNER, REPO, "sha-fail")).thenReturn(List.of());

            CheckRun failedCheck = CheckRun.builder()
                    .id(300L).name("ESLint").appSlug("github-actions")
                    .conclusion("failure").status("completed")
                    .matchedTool(QualityGateTool.ESLINT).matchConfidence(0.7).build();
            when(checkRunMatcher.parseAndMatch(rawCheckRun)).thenReturn(failedCheck);

            when(github.getPullRequest(OWNER, REPO, 9)).thenReturn(Optional.of(Map.of(
                    "state", "closed",
                    "merged_at", "2024-01-02T00:00:00Z",
                    "title", "Merged anyway",
                    "created_at", "2024-01-01T00:00:00Z",
                    "closed_at", "2024-01-02T00:00:00Z"
            )));

            when(gitHistoryAnalyzer.analyzeHistory(eq(OWNER), eq(REPO), anyList())).thenReturn(null);

            RepositoryDetectionResult result = service.detect(REPO_URL);

            EnforcementDetectionResult enforcement = result.getEnforcement();
            assertThat(enforcement.getMergedWithFailure()).isEqualTo(1);
            assertThat(enforcement.getStatus()).isEqualTo(EnforcementStatus.NOT_ENFORCED);
            assertThat(enforcement.getScore()).isEqualTo(0.0);
            assertThat(enforcement.getSamplePRs().getFirst().getOutcome()).isEqualTo(PROutcome.MERGED_WITH_FAILURE);
            assertThat(result.getRecommendation()).isEqualTo("NOT_SUITABLE - Quality gate not enforcing");
        }

        @Test
        void detect_prStillOpen_yieldsStillOpenOutcomeAndNoFailuresFallback() {
            QualityGateDetection detection = simpleDetection(QualityGateTool.PMD, false);
            setUpWorkflowScan(detection, true);

            WorkflowRun failingRun = WorkflowRun.builder()
                    .id(1L).workflowFile(WORKFLOW_FILE).headSha("sha-fail")
                    .conclusion("failure").status("completed").event("pull_request")
                    .prNumber(11).createdAt(Instant.parse("2024-01-01T00:00:00Z")).build();

            Map<String, Object> rawRun1 = Map.of("id", 1);
            when(github.getWorkflowRuns(eq(OWNER), eq(REPO), eq(WORKFLOW_FILE), anyInt()))
                    .thenReturn(List.of(rawRun1));
            when(github.parseWorkflowRun(rawRun1, WORKFLOW_FILE)).thenReturn(failingRun);

            Map<String, Object> rawCheckRun = Map.of("id", 400);
            when(github.getCheckRuns(OWNER, REPO, "sha-fail")).thenReturn(List.of(rawCheckRun));
            when(github.getCommitStatuses(OWNER, REPO, "sha-fail")).thenReturn(List.of());

            CheckRun failedCheck = CheckRun.builder()
                    .id(400L).name("PMD").appSlug("github-actions")
                    .conclusion("failure").status("completed")
                    .matchedTool(QualityGateTool.PMD).matchConfidence(0.7).build();
            when(checkRunMatcher.parseAndMatch(rawCheckRun)).thenReturn(failedCheck);

            when(github.getPullRequest(OWNER, REPO, 11)).thenReturn(Optional.of(Map.of(
                    "state", "open",
                    "title", "WIP",
                    "created_at", "2024-01-01T00:00:00Z"
            )));

            when(gitHistoryAnalyzer.analyzeHistory(eq(OWNER), eq(REPO), anyList())).thenReturn(null);

            RepositoryDetectionResult result = service.detect(REPO_URL);

            EnforcementDetectionResult enforcement = result.getEnforcement();
            assertThat(enforcement.getStillOpen()).isEqualTo(1);
            // still-open provides no enforcement evidence -> total is 0 -> fallback status path
            assertThat(enforcement.getStatus()).isEqualTo(EnforcementStatus.QG_ACTIVE_NO_FAILURES);
            assertThat(enforcement.getFallbackInfo()).isNotNull();
            assertThat(enforcement.getFallbackInfo().isWorkflowRunsOnPRs()).isTrue();
            assertThat(result.getRecommendation()).isEqualTo("POSSIBLY_SUITABLE - QG active but no failures to verify");
        }

        @Test
        void detect_prWithNoMatchingPrDetails_isSkipped() {
            QualityGateDetection detection = simpleDetection(QualityGateTool.PMD, false);
            setUpWorkflowScan(detection, true);

            WorkflowRun failingRun = WorkflowRun.builder()
                    .id(1L).workflowFile(WORKFLOW_FILE).headSha("sha-fail")
                    .conclusion("failure").status("completed").event("pull_request")
                    .prNumber(11).createdAt(Instant.parse("2024-01-01T00:00:00Z")).build();

            Map<String, Object> rawRun1 = Map.of("id", 1);
            when(github.getWorkflowRuns(eq(OWNER), eq(REPO), eq(WORKFLOW_FILE), anyInt()))
                    .thenReturn(List.of(rawRun1));
            when(github.parseWorkflowRun(rawRun1, WORKFLOW_FILE)).thenReturn(failingRun);

            Map<String, Object> rawCheckRun = Map.of("id", 400);
            when(github.getCheckRuns(OWNER, REPO, "sha-fail")).thenReturn(List.of(rawCheckRun));
            when(github.getCommitStatuses(OWNER, REPO, "sha-fail")).thenReturn(List.of());

            CheckRun failedCheck = CheckRun.builder()
                    .id(400L).name("PMD").appSlug("github-actions")
                    .conclusion("failure").status("completed")
                    .matchedTool(QualityGateTool.PMD).matchConfidence(0.7).build();
            when(checkRunMatcher.parseAndMatch(rawCheckRun)).thenReturn(failedCheck);

            // PR was deleted / not found
            when(github.getPullRequest(OWNER, REPO, 11)).thenReturn(Optional.empty());
            when(gitHistoryAnalyzer.analyzeHistory(eq(OWNER), eq(REPO), anyList())).thenReturn(null);

            RepositoryDetectionResult result = service.detect(REPO_URL);

            assertThat(result.getEnforcement().getSamplePRs()).isEmpty();
            assertThat(result.getEnforcement().getTotalPRsChecked()).isEqualTo(1);
            assertThat(result.getEnforcement().getPrsWithQGFailures()).isZero();
        }

        @Test
        void detect_noWorkflowFailures_butExternalToolFailureFoundViaRecentPRs() {
            QualityGateDetection detection = simpleDetection(QualityGateTool.SONARQUBE, false);
            setUpWorkflowScan(detection, true);

            // no workflow runs at all for the (only) tracked workflow
            when(github.getWorkflowRuns(eq(OWNER), eq(REPO), eq(WORKFLOW_FILE), anyInt()))
                    .thenReturn(List.of());

            Map<String, Object> head = Map.of("sha", "sha-recent");
            Map<String, Object> recentPr = new HashMap<>();
            recentPr.put("number", 55);
            recentPr.put("head", head);
            when(github.getRecentClosedPRs(eq(OWNER), eq(REPO), anyInt())).thenReturn(List.of(recentPr));

            Map<String, Object> rawCheckRun = Map.of("id", 500);
            when(github.getCheckRuns(OWNER, REPO, "sha-recent")).thenReturn(List.of(rawCheckRun));
            when(github.getCommitStatuses(OWNER, REPO, "sha-recent")).thenReturn(List.of());

            CheckRun failedCheck = CheckRun.builder()
                    .id(500L).name("SonarQube").appSlug("sonarqube")
                    .conclusion("failure").status("completed")
                    .matchedTool(QualityGateTool.SONARQUBE).matchConfidence(0.9).build();
            when(checkRunMatcher.parseAndMatch(rawCheckRun)).thenReturn(failedCheck);

            when(github.getPullRequest(OWNER, REPO, 55)).thenReturn(Optional.of(Map.of(
                    "state", "closed",
                    "merged_at", "2024-02-01T00:00:00Z",
                    "title", "External QG failure",
                    "created_at", "2024-01-30T00:00:00Z",
                    "closed_at", "2024-02-01T00:00:00Z"
            )));

            when(gitHistoryAnalyzer.analyzeHistory(eq(OWNER), eq(REPO), anyList())).thenReturn(null);

            RepositoryDetectionResult result = service.detect(REPO_URL);

            assertThat(result.getEnforcement().getMergedWithFailure()).isEqualTo(1);
            assertThat(result.getEnforcement().getSamplePRs().getFirst().getFailedQGTools())
                    .containsExactly(QualityGateTool.SONARQUBE);
        }

        @Test
        void detect_recentPrWithoutHeadSha_isSkippedInPartB() {
            QualityGateDetection detection = simpleDetection(QualityGateTool.SONARQUBE, false);
            setUpWorkflowScan(detection, true);
            when(github.getWorkflowRuns(eq(OWNER), eq(REPO), eq(WORKFLOW_FILE), anyInt()))
                    .thenReturn(List.of());

            Map<String, Object> recentPrNoHead = new HashMap<>();
            recentPrNoHead.put("number", 60);
            recentPrNoHead.put("head", null);
            when(github.getRecentClosedPRs(eq(OWNER), eq(REPO), anyInt())).thenReturn(List.of(recentPrNoHead));
            when(gitHistoryAnalyzer.analyzeHistory(eq(OWNER), eq(REPO), anyList())).thenReturn(null);

            RepositoryDetectionResult result = service.detect(REPO_URL);

            verify(github, never()).getCheckRuns(anyString(), anyString(), eq((String) null));
            assertThat(result.getEnforcement().getPrsWithQGFailures()).isZero();
        }

        @Test
        void detect_buildConfigDetectionLinksToExistingWorkflow() {
            // Workflow already tracked via 1A (has a maven-related build command) - build config detection
            // in 1B must NOT create a duplicate QualityGateWorkflow entry for the same file.
            QualityGateDetection workflowDetection = simpleDetection(QualityGateTool.CHECKSTYLE, false);
            when(github.getRepository(OWNER, REPO)).thenReturn(Optional.of(repoInfoMap()));
            when(github.listWorkflowFiles(OWNER, REPO)).thenReturn(List.of(WORKFLOW_FILE));
            stubNoFilesByDefault();
            when(github.getFileContent(OWNER, REPO, WORKFLOW_FILE))
                    .thenReturn(Optional.of("name: CI\nmvn checkstyle:check"));
            when(configParser.parseWorkflow(eq(WORKFLOW_FILE), anyString()))
                    .thenReturn(new WorkflowParseResult(List.of(workflowDetection), true, List.of("maven")));

            QualityGateDetection buildConfigDetection = QualityGateDetection.builder()
                    .tool(QualityGateTool.CHECKSTYLE)
                    .category(QualityGateCategory.CODE_STYLE)
                    .sourceFile("pom.xml")
                    .sourceType(QualityGateDetection.SourceType.BUILD_TOOL)
                    .evidenceFound(List.of("maven-checkstyle-plugin"))
                    .confidenceScore(0.6)
                    .associatedWorkflow(WORKFLOW_FILE)
                    .build();
            when(github.getFileContent(OWNER, REPO, "pom.xml")).thenReturn(Optional.of("<project></project>"));
            when(configParser.parseBuildConfig(eq("pom.xml"), anyString(), eq(WORKFLOW_FILE)))
                    .thenReturn(List.of(buildConfigDetection));

            when(github.getWorkflowRuns(eq(OWNER), eq(REPO), eq(WORKFLOW_FILE), anyInt())).thenReturn(List.of());
            when(gitHistoryAnalyzer.analyzeHistory(eq(OWNER), eq(REPO), anyList())).thenReturn(null);

            RepositoryDetectionResult result = service.detect(REPO_URL);

            long workflowEntriesForFile = result.getQualityGateWorkflows().stream()
                    .filter(w -> w.getWorkflowFile().equals(WORKFLOW_FILE))
                    .count();
            assertThat(workflowEntriesForFile).isEqualTo(1);
        }

        @Test
        void detect_buildConfigDetectionWithNoAssociatedWorkflow_addsDetectionOnly() {
            when(github.getRepository(OWNER, REPO)).thenReturn(Optional.of(repoInfoMap()));
            when(github.listWorkflowFiles(OWNER, REPO)).thenReturn(List.of());
            stubNoFilesByDefault();

            QualityGateDetection buildConfigDetection = QualityGateDetection.builder()
                    .tool(QualityGateTool.JACOCO)
                    .category(QualityGateCategory.COVERAGE)
                    .sourceFile("pom.xml")
                    .sourceType(QualityGateDetection.SourceType.BUILD_TOOL)
                    .evidenceFound(List.of("jacoco-maven-plugin"))
                    .confidenceScore(0.6)
                    .build();
            when(github.getFileContent(OWNER, REPO, "pom.xml")).thenReturn(Optional.of("<project></project>"));
            when(configParser.parseBuildConfig(eq("pom.xml"), anyString(), isNull()))
                    .thenReturn(List.of(buildConfigDetection));

            when(gitHistoryAnalyzer.analyzeHistory(eq(OWNER), eq(REPO), anyList())).thenReturn(null);

            RepositoryDetectionResult result = service.detect(REPO_URL);

            assertThat(result.getQualityGateWorkflows()).isEmpty();
            assertThat(result.getAllDetections()).contains(buildConfigDetection);
        }

        @Test
        void detect_dedicatedConfigFileDetection_isIncluded() {
            when(github.getRepository(OWNER, REPO)).thenReturn(Optional.of(repoInfoMap()));
            when(github.listWorkflowFiles(OWNER, REPO)).thenReturn(List.of());
            stubNoFilesByDefault();

            QualityGateDetection configDetection = simpleDetection(QualityGateTool.CODECOV, false);
            when(github.getFileContent(OWNER, REPO, "codecov.yml")).thenReturn(Optional.of("coverage: {}"));
            when(configParser.parseConfigFile(eq("codecov.yml"), anyString()))
                    .thenReturn(Optional.of(configDetection));

            when(gitHistoryAnalyzer.analyzeHistory(eq(OWNER), eq(REPO), anyList())).thenReturn(null);

            RepositoryDetectionResult result = service.detect(REPO_URL);

            assertThat(result.getAllDetections()).contains(configDetection);
        }

        @Test
        void detect_workflowWithNoDetections_isNotAddedAsQualityGateWorkflow() {
            when(github.getRepository(OWNER, REPO)).thenReturn(Optional.of(repoInfoMap()));
            when(github.listWorkflowFiles(OWNER, REPO)).thenReturn(List.of(WORKFLOW_FILE));
            stubNoFilesByDefault();
            when(github.getFileContent(OWNER, REPO, WORKFLOW_FILE)).thenReturn(Optional.of("name: CI\necho hi"));
            when(configParser.parseWorkflow(eq(WORKFLOW_FILE), anyString()))
                    .thenReturn(new WorkflowParseResult(List.of(), false, List.of()));

            RepositoryDetectionResult result = service.detect(REPO_URL);

            assertThat(result.isHasQualityGate()).isFalse();
            assertThat(result.getQualityGateWorkflows()).isEmpty();
        }

        @Test
        void detect_historyDetectionFails_logsWarningBranch() {
            QualityGateDetection detection = simpleDetection(QualityGateTool.CHECKSTYLE, false);
            setUpWorkflowScan(detection, true);
            when(github.getWorkflowRuns(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of());

            QualityGateHistoryDetection failedHistory = QualityGateHistoryDetection.builder()
                    .metadata(QualityGateHistoryDetection.HistoryDetectionMetadata.builder()
                            .success(false).errorMessage("git clone failed").build())
                    .build();
            when(gitHistoryAnalyzer.analyzeHistory(eq(OWNER), eq(REPO), anyList())).thenReturn(failedHistory);

            RepositoryDetectionResult result = service.detect(REPO_URL);

            assertThat(result.getQualityGateHistory().getMetadata().isSuccess()).isFalse();
        }

        private QualityGateDetection simpleDetection(QualityGateTool tool, boolean triggersOnPR) {
            return QualityGateDetection.builder()
                    .tool(tool)
                    .category(tool.getCategory())
                    .sourceFile(WORKFLOW_FILE)
                    .sourceType(QualityGateDetection.SourceType.WORKFLOW_COMMAND)
                    .evidenceFound(List.of("keyword: " + tool.getDisplayName()))
                    .confidenceScore(0.7)
                    .triggersOnPR(triggersOnPR)
                    .build();
        }

        private void setUpWorkflowScan(QualityGateDetection detection, boolean triggersOnPR) {
            when(github.getRepository(OWNER, REPO)).thenReturn(Optional.of(repoInfoMap()));
            when(github.listWorkflowFiles(OWNER, REPO)).thenReturn(List.of(WORKFLOW_FILE));
            stubNoFilesByDefault();
            when(github.getFileContent(OWNER, REPO, WORKFLOW_FILE))
                    .thenReturn(Optional.of("name: CI\nsome content"));
            when(configParser.parseWorkflow(eq(WORKFLOW_FILE), anyString()))
                    .thenReturn(new WorkflowParseResult(List.of(detection), triggersOnPR, List.of()));
        }
    }

    // ============================================================
    // Direct tests of private helper methods (reflection).
    // Several branches below are unreachable via detect() given the current
    // production wiring (createDefaultPhase15 always marks every detected tool
    // as "required", and branch protection is always null) -- exercising them
    // still matters for coverage/regression-safety of the underlying logic.
    // ============================================================

    @Nested
    class HelperMethods {

        private Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
            Method m = QualityGateDetectionServiceImpl.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(service, args);
        }

        @Test
        void extractWorkflowName_returnsUnknown_whenNoNamePattern() throws Exception {
            String result = (String) invoke("extractWorkflowName", new Class[]{String.class}, "on: push\njobs: {}");
            assertThat(result).isEqualTo("Unknown");
        }

        @Test
        void extractWorkflowName_extractsQuotedName() throws Exception {
            String result = (String) invoke("extractWorkflowName", new Class[]{String.class}, "name: 'My CI'\non: push");
            assertThat(result).isEqualTo("My CI");
        }

        @ParameterizedTest
        @ValueSource(strings = {"pom.xml", "build.gradle", "package.json", "pyproject.toml", "setup.cfg", "Cargo.toml", "go.mod", "composer.json"})
        void findAssociatedWorkflow_returnsNull_whenNoWorkflowUsesMatchingBuildTool(String configPath) throws Exception {
            Object result = invoke("findAssociatedWorkflow",
                    new Class[]{String.class, Map.class}, configPath, Map.of());
            assertThat(result).isNull();
        }

        @Test
        void findAssociatedWorkflow_matchesMavenWorkflow() throws Exception {
            Map<String, List<String>> workflowCommands = Map.of(WORKFLOW_FILE, List.of("maven"));
            Object result = invoke("findAssociatedWorkflow",
                    new Class[]{String.class, Map.class}, "pom.xml", workflowCommands);
            assertThat(result).isEqualTo(WORKFLOW_FILE);
        }

        @Test
        void findAssociatedWorkflow_matchesGradleWorkflow() throws Exception {
            Map<String, List<String>> workflowCommands = Map.of(WORKFLOW_FILE, List.of("gradle"));
            Object result = invoke("findAssociatedWorkflow",
                    new Class[]{String.class, Map.class}, "build.gradle", workflowCommands);
            assertThat(result).isEqualTo(WORKFLOW_FILE);
        }

        @Test
        void findAssociatedWorkflow_matchesNpmWorkflow() throws Exception {
            Map<String, List<String>> workflowCommands = Map.of(WORKFLOW_FILE, List.of("npm"));
            Object result = invoke("findAssociatedWorkflow",
                    new Class[]{String.class, Map.class}, "package.json", workflowCommands);
            assertThat(result).isEqualTo(WORKFLOW_FILE);
        }

        @Test
        void findAssociatedWorkflow_matchesPythonWorkflow() throws Exception {
            Map<String, List<String>> workflowCommands = Map.of(WORKFLOW_FILE, List.of("python"));
            Object result = invoke("findAssociatedWorkflow",
                    new Class[]{String.class, Map.class}, "setup.cfg", workflowCommands);
            assertThat(result).isEqualTo(WORKFLOW_FILE);
        }

        @Test
        void findAssociatedWorkflow_matchesCargoWorkflow() throws Exception {
            Map<String, List<String>> workflowCommands = Map.of(WORKFLOW_FILE, List.of("cargo"));
            Object result = invoke("findAssociatedWorkflow",
                    new Class[]{String.class, Map.class}, "Cargo.toml", workflowCommands);
            assertThat(result).isEqualTo(WORKFLOW_FILE);
        }

        @Test
        void findAssociatedWorkflow_matchesGoWorkflow() throws Exception {
            Map<String, List<String>> workflowCommands = Map.of(WORKFLOW_FILE, List.of("go"));
            Object result = invoke("findAssociatedWorkflow",
                    new Class[]{String.class, Map.class}, "go.mod", workflowCommands);
            assertThat(result).isEqualTo(WORKFLOW_FILE);
        }

        @Test
        void findAssociatedWorkflow_unrecognizedConfigFile_returnsNull() throws Exception {
            Object result = invoke("findAssociatedWorkflow",
                    new Class[]{String.class, Map.class}, "composer.json", Map.of(WORKFLOW_FILE, List.of("php")));
            assertThat(result).isNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void deduplicateDetections_keepsHighestConfidencePerTool() throws Exception {
            QualityGateDetection low = QualityGateDetection.builder()
                    .tool(QualityGateTool.ESLINT).category(QualityGateCategory.CODE_STYLE)
                    .sourceFile("a").sourceType(QualityGateDetection.SourceType.CONFIG_FILE)
                    .evidenceFound(List.of()).confidenceScore(0.3).build();
            QualityGateDetection high = QualityGateDetection.builder()
                    .tool(QualityGateTool.ESLINT).category(QualityGateCategory.CODE_STYLE)
                    .sourceFile("b").sourceType(QualityGateDetection.SourceType.CONFIG_FILE)
                    .evidenceFound(List.of()).confidenceScore(0.9).build();

            List<QualityGateDetection> result = (List<QualityGateDetection>) invoke(
                    "deduplicateDetections", new Class[]{List.class}, List.of(low, high));

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getConfidenceScore()).isEqualTo(0.9);
        }

        @Test
        void determineOutcome_open_returnsStillOpen() throws Exception {
            Object result = invoke("determineOutcome",
                    new Class[]{boolean.class, String.class, boolean.class, boolean.class},
                    false, "open", false, true);
            assertThat(result).isEqualTo(PROutcome.STILL_OPEN);
        }

        @Test
        void determineOutcome_closedNotMerged_returnsBlocked() throws Exception {
            Object result = invoke("determineOutcome",
                    new Class[]{boolean.class, String.class, boolean.class, boolean.class},
                    false, "closed", false, true);
            assertThat(result).isEqualTo(PROutcome.BLOCKED);
        }

        @Test
        void determineOutcome_mergedAndLastRunPassed_returnsFixedThenMerged() throws Exception {
            Object result = invoke("determineOutcome",
                    new Class[]{boolean.class, String.class, boolean.class, boolean.class},
                    true, "closed", true, true);
            assertThat(result).isEqualTo(PROutcome.FIXED_THEN_MERGED);
        }

        @Test
        void determineOutcome_mergedAndStillFailing_returnsMergedWithFailure() throws Exception {
            Object result = invoke("determineOutcome",
                    new Class[]{boolean.class, String.class, boolean.class, boolean.class},
                    true, "closed", false, true);
            assertThat(result).isEqualTo(PROutcome.MERGED_WITH_FAILURE);
        }

        @Test
        void buildOutcomeReason_allBranches() throws Exception {
            Class<?>[] types = {PROutcome.class, List.class, boolean.class};
            List<QualityGateTool> tools = List.of(QualityGateTool.ESLINT);

            assertThat((String) invoke("buildOutcomeReason", types, PROutcome.FIXED_THEN_MERGED, tools, true))
                    .contains("Developer fixed");
            assertThat((String) invoke("buildOutcomeReason", types, PROutcome.BLOCKED, tools, true))
                    .contains("REQUIRED").contains("closed without merge");
            assertThat((String) invoke("buildOutcomeReason", types, PROutcome.MERGED_WITH_FAILURE, tools, false))
                    .contains("informational").contains("Merged despite");
            assertThat((String) invoke("buildOutcomeReason", types, PROutcome.STILL_OPEN, tools, true))
                    .contains("Still open");
            assertThat((String) invoke("buildOutcomeReason", types, PROutcome.NO_FAILURE, tools, true))
                    .isEqualTo("No QG failure");
        }

        @Test
        void isExternalQGTool_trueForKnownExternalApps() throws Exception {
            for (QualityGateTool tool : List.of(QualityGateTool.SONARCLOUD, QualityGateTool.SONARQUBE,
                    QualityGateTool.CODECOV, QualityGateTool.COVERALLS, QualityGateTool.CODACY,
                    QualityGateTool.CODE_CLIMATE, QualityGateTool.DEEPSOURCE)) {
                Object result = invoke("isExternalQGTool", new Class[]{QualityGateTool.class}, tool);
                assertThat((Boolean) result).as(tool.name()).isTrue();
            }
        }

        @Test
        void isExternalQGTool_falseForNonExternalTool() throws Exception {
            Object result = invoke("isExternalQGTool", new Class[]{QualityGateTool.class}, QualityGateTool.CHECKSTYLE);
            assertThat((Boolean) result).isFalse();
        }

        @Test
        void parseInstant_nullInput_returnsNull() throws Exception {
            Object result = invoke("parseInstant", new Class[]{String.class}, (Object) null);
            assertThat(result).isNull();
        }

        @Test
        void parseInstant_invalidFormat_returnsNull() throws Exception {
            Object result = invoke("parseInstant", new Class[]{String.class}, "not-a-date");
            assertThat(result).isNull();
        }

        @Test
        void parseInstant_validFormat_parsesCorrectly() throws Exception {
            Object result = invoke("parseInstant", new Class[]{String.class}, "2024-01-01T00:00:00Z");
            assertThat(result).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        }

        @Test
        void buildConclusion_noDetections_returnsFixedMessage() throws Exception {
            Object phase1 = newPhase1Result(List.of(), List.of(), List.of());
            Object phase15 = null;
            String result = (String) invoke("buildConclusion",
                    new Class[]{phase1Class(), phase15Class(), EnforcementDetectionResult.class},
                    phase1, phase15, null);
            assertThat(result).isEqualTo("No thesis-relevant quality gates detected in this repository.");
        }

        @Test
        void buildConclusion_withDetectionsButNullEnforcement_omitsEnforcementSentence() throws Exception {
            QualityGateDetection detection = QualityGateDetection.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE)
                    .sourceFile("a").sourceType(QualityGateDetection.SourceType.CONFIG_FILE)
                    .evidenceFound(List.of()).confidenceScore(0.5).build();
            Object phase1 = newPhase1Result(List.of(detection), List.of(detection), List.of());

            String result = (String) invoke("buildConclusion",
                    new Class[]{phase1Class(), phase15Class(), EnforcementDetectionResult.class},
                    phase1, null, null);

            assertThat(result).isEqualTo("Found quality gate(s): PMD. ");
        }

        @Test
        void buildRecommendation_requiredToolsEmpty_returnsInformationalOnlyMessage() throws Exception {
            QualityGateDetection detection = QualityGateDetection.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE)
                    .sourceFile("a").sourceType(QualityGateDetection.SourceType.CONFIG_FILE)
                    .evidenceFound(List.of()).confidenceScore(0.5).build();
            Object phase1 = newPhase1Result(List.of(detection), List.of(detection), List.of());
            Object phase15 = newPhase15Result(null, Set.of(), Set.of());

            String result = (String) invoke("buildRecommendation",
                    new Class[]{phase1Class(), phase15Class(), EnforcementDetectionResult.class},
                    phase1, phase15, null);

            assertThat(result).isEqualTo("NOT_SUITABLE - QG tools are informational only (not required for merge)");
        }

        @Test
        void buildRecommendation_nullEnforcement_returnsUnknown() throws Exception {
            QualityGateDetection detection = QualityGateDetection.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE)
                    .sourceFile("a").sourceType(QualityGateDetection.SourceType.CONFIG_FILE)
                    .evidenceFound(List.of()).confidenceScore(0.5).build();
            Object phase1 = newPhase1Result(List.of(detection), List.of(detection), List.of());
            Object phase15 = newPhase15Result(null, Set.of(QualityGateTool.PMD), Set.of());

            String result = (String) invoke("buildRecommendation",
                    new Class[]{phase1Class(), phase15Class(), EnforcementDetectionResult.class},
                    phase1, phase15, null);

            assertThat(result).isEqualTo("UNKNOWN - Enforcement detection not performed");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "MOSTLY_ENFORCED", "PARTIALLY_ENFORCED", "QG_NOT_RUNNING_ON_PRS", "QG_NOT_REQUIRED",
                "CONFIGURED_NOT_VERIFIED", "NO_QUALITY_GATE", "INSUFFICIENT_DATA"
        })
        void buildRecommendation_allEnforcementStatuses(String statusName) throws Exception {
            QualityGateDetection detection = QualityGateDetection.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE)
                    .sourceFile("a").sourceType(QualityGateDetection.SourceType.CONFIG_FILE)
                    .evidenceFound(List.of()).confidenceScore(0.5).build();
            Object phase1 = newPhase1Result(List.of(detection), List.of(detection), List.of());
            Object phase15 = newPhase15Result(null, Set.of(QualityGateTool.PMD), Set.of());
            EnforcementStatus status = EnforcementStatus.valueOf(statusName);
            EnforcementDetectionResult enforcement = EnforcementDetectionResult.builder().status(status).build();

            String result = (String) invoke("buildRecommendation",
                    new Class[]{phase1Class(), phase15Class(), EnforcementDetectionResult.class},
                    phase1, phase15, enforcement);

            assertThat(result).isNotBlank();
        }

        @Test
        void buildFallbackReason_noRequiredTools() throws Exception {
            Object phase2 = newPhase2Result(List.of(), List.of(), Map.of());
            Object phase1 = newPhase1Result(List.of(), List.of(), List.of());
            Object phase15 = newPhase15Result(null, Set.of(), Set.of());

            String result = (String) invoke("buildFallbackReason",
                    new Class[]{phase2Class(), phase1Class(), phase15Class()}, phase2, phase1, phase15);

            assertThat(result).isEqualTo("No QG tools are configured as required status checks");
        }

        @Test
        void buildFallbackReason_noPrRelatedRuns() throws Exception {
            Object phase2 = newPhase2Result(List.of(), List.of(), Map.of());
            Object phase1 = newPhase1Result(List.of(), List.of(), List.of());
            Object phase15 = newPhase15Result(null, Set.of(QualityGateTool.PMD), Set.of());

            String result = (String) invoke("buildFallbackReason",
                    new Class[]{phase2Class(), phase1Class(), phase15Class()}, phase2, phase1, phase15);

            assertThat(result).isEqualTo("Quality gate workflows don't appear to run on PRs");
        }

        @Test
        void buildFallbackReason_allWorkflowRunsPassed() throws Exception {
            WorkflowRun successRun = WorkflowRun.builder().id(1L).conclusion("success").event("pull_request").build();
            Object phase2 = newPhase2Result(List.of(successRun), List.of(successRun), Map.of());
            Object phase1 = newPhase1Result(List.of(), List.of(), List.of());
            Object phase15 = newPhase15Result(null, Set.of(QualityGateTool.PMD), Set.of());

            String result = (String) invoke("buildFallbackReason",
                    new Class[]{phase2Class(), phase1Class(), phase15Class()}, phase2, phase1, phase15);

            assertThat(result).isEqualTo("All workflow runs passed - team may have high quality standards or thresholds may be loose");
        }

        @Test
        void buildFallbackReason_failuresButUnverified() throws Exception {
            WorkflowRun failedRun = WorkflowRun.builder().id(1L).conclusion("failure").event("pull_request").build();
            Object phase2 = newPhase2Result(List.of(failedRun), List.of(failedRun), Map.of());
            Object phase1 = newPhase1Result(List.of(), List.of(), List.of());
            Object phase15 = newPhase15Result(null, Set.of(QualityGateTool.PMD), Set.of());

            String result = (String) invoke("buildFallbackReason",
                    new Class[]{phase2Class(), phase1Class(), phase15Class()}, phase2, phase1, phase15);

            assertThat(result).isEqualTo("Workflow failures found but couldn't verify they were QG-related");
        }

        @Test
        void buildInterpretation_requiredToolsEmpty() throws Exception {
            Object phase3 = newPhase3Result(List.of(), 0, 0, 0, 0, 0, Map.of());
            Object phase15 = newPhase15Result(null, Set.of(), Set.of());

            String result = (String) invoke("buildInterpretation",
                    new Class[]{EnforcementStatus.class, phase3Class(), int.class, Double.class, phase15Class()},
                    EnforcementStatus.QG_NOT_REQUIRED, phase3, 0, null, phase15);

            assertThat(result).contains("informational only");
        }

        @Test
        void buildInterpretation_totalZero_returnsStatusDescription() throws Exception {
            Object phase3 = newPhase3Result(List.of(), 0, 0, 0, 0, 0, Map.of());
            Object phase15 = newPhase15Result(null, Set.of(QualityGateTool.PMD), Set.of());

            String result = (String) invoke("buildInterpretation",
                    new Class[]{EnforcementStatus.class, phase3Class(), int.class, Double.class, phase15Class()},
                    EnforcementStatus.QG_ACTIVE_NO_FAILURES, phase3, 0, null, phase15);

            assertThat(result).isEqualTo(EnforcementStatus.QG_ACTIVE_NO_FAILURES.getDescription());
        }

        @Test
        void buildInterpretation_withInformationalFailures_appendsExtraSentence() throws Exception {
            Object phase3 = newPhase3Result(List.of(), 1, 0, 1, 0, 1, Map.of());
            Object phase15 = newPhase15Result(null, Set.of(QualityGateTool.PMD), Set.of());

            String result = (String) invoke("buildInterpretation",
                    new Class[]{EnforcementStatus.class, phase3Class(), int.class, Double.class, phase15Class()},
                    EnforcementStatus.PARTIALLY_ENFORCED, phase3, 2, 0.5, phase15);

            assertThat(result).contains("INFORMATIONAL QG failures");
        }

        // --- reflection helpers for private nested record types ---

        private Class<?> phase1Class() throws ClassNotFoundException {
            return nested("Phase1Result");
        }

        private Class<?> phase15Class() throws ClassNotFoundException {
            return nested("Phase15Result");
        }

        private Class<?> phase2Class() throws ClassNotFoundException {
            return nested("Phase2Result");
        }

        private Class<?> phase3Class() throws ClassNotFoundException {
            return nested("Phase3Result");
        }

        private Class<?> nested(String simpleName) throws ClassNotFoundException {
            return Class.forName(QualityGateDetectionServiceImpl.class.getName() + "$" + simpleName);
        }

        private Object newPhase1Result(List<QualityGateDetection> all, List<QualityGateDetection> relevant,
                                       List<QualityGateWorkflow> workflows) throws Exception {
            var ctor = phase1Class().getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return ctor.newInstance(all, relevant, workflows);
        }

        private Object newPhase15Result(BranchProtection bp, Set<QualityGateTool> required,
                                        Set<QualityGateTool> informational) throws Exception {
            var ctor = phase15Class().getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return ctor.newInstance(bp, required, informational);
        }

        private Object newPhase2Result(List<WorkflowRun> allRuns, List<WorkflowRun> prRelated,
                                       Map<Integer, ?> prsWithFailures) throws Exception {
            var ctor = phase2Class().getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return ctor.newInstance(allRuns, prRelated, prsWithFailures);
        }

        private Object newPhase3Result(List<PRDetectionResult> prResults, int fixedThenMerged, int blocked,
                                       int mergedWithFailure, int stillOpen, int mergedWithInformationalFailure,
                                       Map<QualityGateTool, int[]> byTool) throws Exception {
            var ctor = phase3Class().getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return ctor.newInstance(prResults, fixedThenMerged, blocked, mergedWithFailure, stillOpen,
                    mergedWithInformationalFailure, byTool);
        }
    }
}
