package com.thesis.qualitygateanalyzer.service.github;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CheckRun;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CommitStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CheckRunMatcherImplTest {

    private final CheckRunMatcherImpl matcher = new CheckRunMatcherImpl();

    private Map<String, Object> rawCheckRun(String name, String appSlug, String conclusion) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("id", 1);
        raw.put("name", name);
        raw.put("status", "completed");
        raw.put("conclusion", conclusion);
        raw.put("head_sha", "sha1");
        if (appSlug != null) {
            raw.put("app", Map.of("slug", appSlug));
        }
        return raw;
    }

    @Nested
    class ParseAndMatchCheckRun {
        @Test
        void matchesByAppSlug_highestPriority() {
            CheckRun result = matcher.parseAndMatch(rawCheckRun("Some Generic Name", "sonarcloud", "success"));
            assertThat(result.getMatchedTool()).isEqualTo(QualityGateTool.SONARCLOUD);
            assertThat(result.getMatchConfidence()).isEqualTo(0.95);
        }

        @Test
        void matchesByEnumCheckRunNamePattern() {
            CheckRun result = matcher.parseAndMatch(rawCheckRun("sonarqube", null, "success"));
            assertThat(result.getMatchedTool()).isEqualTo(QualityGateTool.SONARQUBE);
        }

        @Test
        void matchesByQuickKeywordFallback_compoundName() {
            CheckRun result = matcher.parseAndMatch(rawCheckRun("my-sonar-cloud-build-step", null, "success"));
            assertThat(result.getMatchedTool()).isEqualTo(QualityGateTool.SONARCLOUD);
        }

        @Test
        void noMatch_returnsNullToolAndZeroConfidence() {
            CheckRun result = matcher.parseAndMatch(rawCheckRun("Build and Test", null, "success"));
            assertThat(result.getMatchedTool()).isNull();
            assertThat(result.getMatchConfidence()).isZero();
        }

        @Test
        void nameContainingSonar_alwaysMatchesViaFallbackKeyword() {
            // Note: any check-run name containing "sonar" or "quality gate" is always resolved by
            // matchCheckRun's own pattern/keyword matching (SONARCLOUD's checkRunNamePatterns include
            // "quality gate"; QUICK_MATCH_KEYWORDS includes "sonar"). The unmatched-failure debug-log
            // branch in parseAndMatch is therefore unreachable via this API - it can never observe
            // matchedTool == null for a name containing either substring.
            CheckRun result = matcher.parseAndMatch(rawCheckRun("sonar-something-unmapped-xyz", null, "failure"));
            assertThat(result.getMatchedTool()).isEqualTo(QualityGateTool.SONARQUBE);
        }

        @Test
        void confidenceIsHigh_whenExactToolNameInCheckName() {
            CheckRun result = matcher.parseAndMatch(rawCheckRun("SonarCloud", null, "success"));
            assertThat(result.getMatchConfidence()).isEqualTo(0.90);
        }

        @Test
        void confidenceIsMediumHigh_whenQualityGatePhrasePresent() {
            CheckRun result = matcher.parseAndMatch(rawCheckRun("quality gate build", null, "success"));
            // "quality gate" matches enum pattern for SONARCLOUD via workflowKeywordPatterns/checkRunNamePatterns
            assertThat(result.getMatchedTool()).isEqualTo(QualityGateTool.SONARCLOUD);
        }

        @Test
        void outputTitleAndSummary_areExtractedAndTruncated() {
            Map<String, Object> raw = rawCheckRun("Checkstyle", null, "failure");
            String longSummary = "x".repeat(600);
            raw.put("output", Map.of("title", "Failed", "summary", longSummary));
            CheckRun result = matcher.parseAndMatch(raw);
            assertThat(result.getOutputTitle()).isEqualTo("Failed");
            assertThat(result.getOutputSummary()).hasSize(503).endsWith("...");
        }

        @Test
        void missingOutputAndApp_areHandledGracefully() {
            CheckRun result = matcher.parseAndMatch(rawCheckRun("Checkstyle", null, "success"));
            assertThat(result.getOutputTitle()).isNull();
            assertThat(result.getAppSlug()).isNull();
        }

        @Test
        void completedAt_parsedWhenPresent() {
            Map<String, Object> raw = rawCheckRun("Checkstyle", null, "success");
            raw.put("completed_at", "2024-01-01T00:00:00Z");
            CheckRun result = matcher.parseAndMatch(raw);
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        void completedAt_invalidFormat_isNull() {
            Map<String, Object> raw = rawCheckRun("Checkstyle", null, "success");
            raw.put("completed_at", "garbage");
            CheckRun result = matcher.parseAndMatch(raw);
            assertThat(result.getCompletedAt()).isNull();
        }
    }

    @Nested
    class ParseAndMatchStatus {
        private Map<String, Object> rawStatus(String context, String state) {
            Map<String, Object> raw = new HashMap<>();
            raw.put("id", 1);
            raw.put("context", context);
            raw.put("state", state);
            raw.put("description", "desc");
            return raw;
        }

        @Test
        void nullContext_returnsNullTool() {
            CommitStatus result = matcher.parseAndMatchStatus(rawStatus(null, "success"));
            assertThat(result.getMatchedTool()).isNull();
            assertThat(result.getMatchConfidence()).isZero();
        }

        @Test
        void blankContext_returnsNullTool() {
            CommitStatus result = matcher.parseAndMatchStatus(rawStatus("   ", "success"));
            assertThat(result.getMatchedTool()).isNull();
        }

        @Test
        void quickKeywordMatch_codecovContext() {
            CommitStatus result = matcher.parseAndMatchStatus(rawStatus("codecov/project", "success"));
            assertThat(result.getMatchedTool()).isEqualTo(QualityGateTool.CODECOV);
            assertThat(result.getMatchConfidence()).isEqualTo(0.95);
        }

        @Test
        void sonarCloudContext_highConfidence() {
            CommitStatus result = matcher.parseAndMatchStatus(rawStatus("SonarCloud Code Analysis", "failure"));
            assertThat(result.getMatchedTool()).isEqualTo(QualityGateTool.SONARCLOUD);
            assertThat(result.getMatchConfidence()).isEqualTo(0.95);
        }

        @Test
        void ciCdProviderWithParenthesizedJobName_matchesEmbeddedTool() {
            CommitStatus result = matcher.parseAndMatchStatus(
                    rawStatus("AWS CodeBuild us-west-2 (my-project-sonarqube-scan)", "success"));
            assertThat(result.getMatchedTool()).isEqualTo(QualityGateTool.SONARQUBE);
        }

        @Test
        void ciCdProviderWithColonJobName_matchesEmbeddedTool() {
            CommitStatus result = matcher.parseAndMatchStatus(rawStatus("circleci: build/pmd-check", "success"));
            assertThat(result.getMatchedTool()).isEqualTo(QualityGateTool.PMD);
        }

        @Test
        void qualityGatePhrase_fallsBackToSonarCloud() {
            CommitStatus result = matcher.parseAndMatchStatus(rawStatus("Custom Quality Gate Status", "success"));
            assertThat(result.getMatchedTool()).isEqualTo(QualityGateTool.SONARCLOUD);
        }

        @Test
        void noMatch_returnsNull() {
            CommitStatus result = matcher.parseAndMatchStatus(rawStatus("random-check-context", "success"));
            assertThat(result.getMatchedTool()).isNull();
        }

        @Test
        void nullId_handledGracefully() {
            Map<String, Object> raw = rawStatus("codecov/project", "success");
            raw.put("id", null);
            CommitStatus result = matcher.parseAndMatchStatus(raw);
            assertThat(result.getId()).isNull();
        }

        @Test
        void createdAndUpdatedAt_parsed() {
            Map<String, Object> raw = rawStatus("codecov/project", "success");
            raw.put("created_at", "2024-01-01T00:00:00Z");
            raw.put("updated_at", "2024-01-02T00:00:00Z");
            CommitStatus result = matcher.parseAndMatchStatus(raw);
            assertThat(result.getCreatedAt()).isNotNull();
            assertThat(result.getUpdatedAt()).isNotNull();
        }
    }

    @Nested
    class FilterHelpers {
        @Test
        void filterQualityGateCheckRuns_keepsOnlyMatched() {
            CheckRun matched = CheckRun.builder().matchedTool(QualityGateTool.PMD).build();
            CheckRun unmatched = CheckRun.builder().build();
            assertThat(matcher.filterQualityGateCheckRuns(List.of(matched, unmatched))).containsExactly(matched);
        }

        @Test
        void filterThesisRelevant_keepsOnlyThesisRelevantTools() {
            CheckRun relevant = CheckRun.builder().matchedTool(QualityGateTool.PMD).build();
            CheckRun security = CheckRun.builder().matchedTool(QualityGateTool.SNYK).build();
            assertThat(matcher.filterThesisRelevant(List.of(relevant, security))).containsExactly(relevant);
        }

        @Test
        void getFailedQGCheckRuns_keepsOnlyFailedAndMatched() {
            CheckRun failed = CheckRun.builder().matchedTool(QualityGateTool.PMD).conclusion("failure").build();
            CheckRun passed = CheckRun.builder().matchedTool(QualityGateTool.PMD).conclusion("success").build();
            assertThat(matcher.getFailedQGCheckRuns(List.of(failed, passed))).containsExactly(failed);
        }

        @Test
        void filterQualityGateStatuses_keepsOnlyMatched() {
            CommitStatus matched = CommitStatus.builder().matchedTool(QualityGateTool.CODECOV).build();
            CommitStatus unmatched = CommitStatus.builder().build();
            assertThat(matcher.filterQualityGateStatuses(List.of(matched, unmatched))).containsExactly(matched);
        }

        @Test
        void getFailedQGStatuses_keepsOnlyFailedAndMatched() {
            CommitStatus failed = CommitStatus.builder().matchedTool(QualityGateTool.CODECOV).state("failure").build();
            CommitStatus passed = CommitStatus.builder().matchedTool(QualityGateTool.CODECOV).state("success").build();
            assertThat(matcher.getFailedQGStatuses(List.of(failed, passed))).containsExactly(failed);
        }
    }

    @Nested
    class ExtractJobNameReflection {
        private String invoke(String context) throws Exception {
            Method m = CheckRunMatcherImpl.class.getDeclaredMethod("extractJobName", String.class);
            m.setAccessible(true);
            return (String) m.invoke(matcher, context);
        }

        @Test
        void parenthesesPattern() throws Exception {
            assertThat(invoke("AWS CodeBuild (my-job-name)")).isEqualTo("my-job-name");
        }

        @Test
        void colonPattern() throws Exception {
            assertThat(invoke("circleci: build/sonar-scan")).isEqualTo("build/sonar-scan");
        }

        @Test
        void arrowSeparatorPattern() throws Exception {
            assertThat(invoke("Jenkins » SonarQube Analysis")).isEqualTo("SonarQube Analysis");
        }

        @Test
        void slashSeparatorPattern() throws Exception {
            assertThat(invoke("provider / job-name")).isEqualTo("job-name");
        }

        @Test
        void dashSeparatorPattern() throws Exception {
            assertThat(invoke("provider - job-name")).isEqualTo("job-name");
        }

        @Test
        void noRecognizedPattern_returnsNull() throws Exception {
            assertThat(invoke("just a plain context")).isNull();
        }
    }

    @Nested
    class TruncateReflection {
        private String invoke(String s, int max) throws Exception {
            Method m = CheckRunMatcherImpl.class.getDeclaredMethod("truncate", String.class, int.class);
            m.setAccessible(true);
            return (String) m.invoke(matcher, s, max);
        }

        @Test
        void nullInput_returnsNull() throws Exception {
            assertThat(invoke(null, 10)).isNull();
        }

        @Test
        void shorterThanMax_isUnchanged() throws Exception {
            assertThat(invoke("short", 10)).isEqualTo("short");
        }

        @Test
        void longerThanMax_isTruncatedWithEllipsis() throws Exception {
            assertThat(invoke("0123456789extra", 10)).isEqualTo("0123456789...");
        }
    }
}
