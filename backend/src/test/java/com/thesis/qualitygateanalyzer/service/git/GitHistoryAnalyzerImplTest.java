package com.thesis.qualitygateanalyzer.service.git;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.*;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHistoryAnalyzerImplTest {

    @Mock
    private GitHubApiClient github;
    @Mock
    private PRBasedIntroductionFinder prBasedFinder;

    private GitHistoryAnalyzerImpl analyzer;

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";

    @BeforeEach
    void setUp() {
        analyzer = new GitHistoryAnalyzerImpl(github, prBasedFinder);
    }

    private QualityGateDetection detection(QualityGateTool tool, String sourceFile, List<String> evidence) {
        return QualityGateDetection.builder()
                .tool(tool).category(tool.getCategory())
                .sourceFile(sourceFile).sourceType(QualityGateDetection.SourceType.CONFIG_FILE)
                .evidenceFound(evidence).confidenceScore(0.7).build();
    }

    private Map<String, Object> rawCommit(String sha, String author, String date, String message) {
        Map<String, Object> authorMap = new HashMap<>();
        authorMap.put("name", author);
        authorMap.put("date", date);
        Map<String, Object> commit = new HashMap<>();
        commit.put("author", authorMap);
        commit.put("message", message);
        Map<String, Object> raw = new HashMap<>();
        raw.put("sha", sha);
        raw.put("commit", commit);
        return raw;
    }

    @Nested
    class AnalyzeHistoryPipeline {

        @Test
        void dedicatedConfigFile_usesFileCreationCommit() {
            QualityGateDetection d = detection(QualityGateTool.CODECOV, "codecov.yml", List.of("codecov config"));
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(10);
            when(github.fileExists(OWNER, REPO, "codecov.yml")).thenReturn(true);
            CommitInfo creation = CommitInfo.builder().sha("abc1234567").shortSha("abc1234")
                    .author("Alice").date(Instant.parse("2022-01-01T00:00:00Z")).build();
            when(github.getFileCreationCommit(OWNER, REPO, "codecov.yml")).thenReturn(creation);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(d));

            assertThat(result.getToolIntroductions()).hasSize(1);
            QGToolIntroduction intro = result.getToolIntroductions().getFirst();
            assertThat(intro.getConfigIntroductions()).hasSize(1);
            assertThat(intro.getCiIntroductions()).isEmpty();
            assertThat(intro.getEffectiveIntroductionCommit()).isEqualTo(creation);
            assertThat(intro.getIntroductionSummary()).contains("introduced in commit");
            assertThat(result.getMetadata().isSuccess()).isTrue();
        }

        @Test
        void dedicatedConfigFile_notFound_returnsNullFileIntroduction_thenFallsBackToPR() {
            QualityGateDetection d = detection(QualityGateTool.CODECOV, "codecov.yml", List.of("codecov config"));
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(0);
            when(github.fileExists(OWNER, REPO, "codecov.yml")).thenReturn(false);
            when(prBasedFinder.findToolIntroduction(OWNER, REPO, QualityGateTool.CODECOV)).thenReturn(null);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(d));

            QGToolIntroduction intro = result.getToolIntroductions().getFirst();
            assertThat(intro.getEffectiveIntroductionCommit()).isNull();
            assertThat(intro.getIntroductionSummary()).contains("could not be determined");
        }

        @Test
        void workflowFile_usesBlameOnMatchedLine() {
            QualityGateDetection d = detection(QualityGateTool.PMD, ".github/workflows/ci.yml", List.of("keyword: pmd"));
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(5);
            when(github.getFileContentString(OWNER, REPO, ".github/workflows/ci.yml"))
                    .thenReturn("name: CI\nrun: pmd check\n");
            CommitInfo blame = CommitInfo.builder().sha("def1234567").shortSha("def1234")
                    .author("Bob").date(Instant.parse("2023-05-01T00:00:00Z")).build();
            when(github.blameLineGraphQL(OWNER, REPO, ".github/workflows/ci.yml", 2)).thenReturn(blame);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(d));

            QGToolIntroduction intro = result.getToolIntroductions().getFirst();
            assertThat(intro.getCiIntroductions()).hasSize(1);
            assertThat(intro.getConfigIntroductions()).isEmpty();
            assertThat(intro.getEffectiveIntroductionCommit()).isEqualTo(blame);
        }

        @Test
        void blameReturnsNull_fallsBackToPRBasedFinder() {
            QualityGateDetection d = detection(QualityGateTool.PMD, ".github/workflows/ci.yml", List.of("keyword: pmd"));
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(5);
            when(github.getFileContentString(OWNER, REPO, ".github/workflows/ci.yml"))
                    .thenReturn("name: CI\nrun: pmd check\n");
            when(github.blameLineGraphQL(OWNER, REPO, ".github/workflows/ci.yml", 2)).thenReturn(null);

            QGFileIntroduction prIntro = QGFileIntroduction.builder()
                    .filePath("PR #3 (external app)").searchPattern("PMD check run")
                    .introducedAt(CommitInfo.builder().sha("xyz").build())
                    .build();
            when(prBasedFinder.findToolIntroduction(OWNER, REPO, QualityGateTool.PMD)).thenReturn(prIntro);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(d));

            QGToolIntroduction intro = result.getToolIntroductions().getFirst();
            assertThat(intro.getCiIntroductions()).containsExactly(prIntro);
        }

        @Test
        void fileContentNull_returnsNullAndFallsBackToPR() {
            QualityGateDetection d = detection(QualityGateTool.PMD, ".github/workflows/ci.yml", List.of("keyword: pmd"));
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(0);
            when(github.getFileContentString(OWNER, REPO, ".github/workflows/ci.yml")).thenReturn(null);
            when(prBasedFinder.findToolIntroduction(OWNER, REPO, QualityGateTool.PMD)).thenReturn(null);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(d));

            assertThat(result.getToolIntroductions().getFirst().getEffectiveIntroductionCommit()).isNull();
        }

        @Test
        void keywordsNotFoundInFile_returnsNull() {
            QualityGateDetection d = detection(QualityGateTool.PMD, ".github/workflows/ci.yml", List.of("keyword: pmd"));
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(0);
            when(github.getFileContentString(OWNER, REPO, ".github/workflows/ci.yml"))
                    .thenReturn("name: CI\nrun: echo hello\n");
            when(prBasedFinder.findToolIntroduction(OWNER, REPO, QualityGateTool.PMD)).thenReturn(null);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(d));

            assertThat(result.getToolIntroductions().getFirst().getEffectiveIntroductionCommit()).isNull();
        }

        @Test
        void nullSourceFile_isSkipped() {
            QualityGateDetection d = detection(QualityGateTool.PMD, null, List.of("keyword: pmd"));
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(0);
            when(prBasedFinder.findToolIntroduction(OWNER, REPO, QualityGateTool.PMD)).thenReturn(null);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(d));

            verify(github, never()).getFileContentString(anyString(), anyString(), anyString());
            assertThat(result.getToolIntroductions()).hasSize(1);
        }

        @Test
        void duplicateSourceFileForSameTool_isProcessedOnce() {
            QualityGateDetection d1 = detection(QualityGateTool.PMD, ".github/workflows/ci.yml", List.of("keyword: pmd"));
            QualityGateDetection d2 = detection(QualityGateTool.PMD, ".github/workflows/ci.yml", List.of("keyword: pmd2"));
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(0);
            when(github.getFileContentString(OWNER, REPO, ".github/workflows/ci.yml")).thenReturn("run: pmd\n");
            when(github.blameLineGraphQL(anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(CommitInfo.builder().sha("s").build());

            analyzer.analyzeHistory(OWNER, REPO, List.of(d1, d2));

            verify(github, times(1)).getFileContentString(OWNER, REPO, ".github/workflows/ci.yml");
        }

        @Test
        void emptyKeywords_fallsBackToToolPatterns_thenAnalyzesBlame() {
            // Evidence is entirely generic ("test", "build") -> extractSearchableKeywords falls back
            // to tool.getBuildToolPatterns()/getWorkflowActionPatterns(), which for CHECKSTYLE include
            // "maven-checkstyle-plugin" among others.
            QualityGateDetection d = detection(QualityGateTool.CHECKSTYLE, "pom.xml", List.of("test", "build"));
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(0);
            when(github.getFileContentString(OWNER, REPO, "pom.xml"))
                    .thenReturn("<plugin>maven-checkstyle-plugin</plugin>");
            when(github.blameLineGraphQL(anyString(), anyString(), anyString(), anyInt()))
                    .thenReturn(CommitInfo.builder().sha("s").date(Instant.parse("2022-06-01T00:00:00Z")).build());

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(d));

            assertThat(result.getToolIntroductions().getFirst().getEffectiveIntroductionCommit()).isNotNull();
        }

        @Test
        void presentSinceRepoCreation_whenIntroducedAtMatchesFirstCommit() {
            QualityGateDetection d = detection(QualityGateTool.CODECOV, "codecov.yml", List.of("codecov config"));
            CommitInfo firstCommit = CommitInfo.builder().sha("root111111").shortSha("root111")
                    .author("Root").date(Instant.parse("2020-01-01T00:00:00Z")).build();
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.of(rawCommit(
                    "root111111", "Root", "2020-01-01T00:00:00Z", "initial commit")));
            when(github.getCommitCount(OWNER, REPO)).thenReturn(1);
            when(github.fileExists(OWNER, REPO, "codecov.yml")).thenReturn(true);
            when(github.getFileCreationCommit(OWNER, REPO, "codecov.yml")).thenReturn(firstCommit);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(d));

            QGToolIntroduction intro = result.getToolIntroductions().getFirst();
            assertThat(intro.isPresentSinceRepoCreation()).isTrue();
            assertThat(intro.getIntroductionSummary()).contains("present since repository creation");
            assertThat(result.getRepoFirstCommit().getSha()).isEqualTo("root111111");
        }

        @Test
        void multipleTools_sortedByEffectiveDate_earliestAndLatestPicked() {
            QualityGateDetection d1 = detection(QualityGateTool.CODECOV, "codecov.yml", List.of("codecov config"));
            QualityGateDetection d2 = detection(QualityGateTool.CHECKSTYLE, "checkstyle.xml", List.of("checkstyle config"));

            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(0);

            when(github.fileExists(OWNER, REPO, "codecov.yml")).thenReturn(true);
            when(github.getFileCreationCommit(OWNER, REPO, "codecov.yml")).thenReturn(
                    CommitInfo.builder().sha("late").date(Instant.parse("2023-01-01T00:00:00Z")).build());

            when(github.fileExists(OWNER, REPO, "checkstyle.xml")).thenReturn(true);
            when(github.getFileCreationCommit(OWNER, REPO, "checkstyle.xml")).thenReturn(
                    CommitInfo.builder().sha("early").date(Instant.parse("2021-01-01T00:00:00Z")).build());

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(d1, d2));

            assertThat(result.getEarliestIntroduction().getTool()).isEqualTo(QualityGateTool.CHECKSTYLE);
            assertThat(result.getLatestIntroduction().getTool()).isEqualTo(QualityGateTool.CODECOV);
            assertThat(result.getToolIntroductions()).extracting(QGToolIntroduction::getTool)
                    .containsExactly(QualityGateTool.CHECKSTYLE, QualityGateTool.CODECOV);
        }

        @Test
        void toolWithNoResolvedDate_isSortedLastAndExcludedFromEarliestLatest() {
            QualityGateDetection undetermined = detection(QualityGateTool.PMD, ".github/workflows/ci.yml", List.of("keyword: pmd"));
            QualityGateDetection resolved = detection(QualityGateTool.CODECOV, "codecov.yml", List.of("codecov config"));

            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(0);
            when(github.getFileContentString(OWNER, REPO, ".github/workflows/ci.yml")).thenReturn("run: echo hi");
            when(prBasedFinder.findToolIntroduction(OWNER, REPO, QualityGateTool.PMD)).thenReturn(null);

            when(github.fileExists(OWNER, REPO, "codecov.yml")).thenReturn(true);
            when(github.getFileCreationCommit(OWNER, REPO, "codecov.yml")).thenReturn(
                    CommitInfo.builder().sha("s").date(Instant.parse("2022-01-01T00:00:00Z")).build());

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(undetermined, resolved));

            assertThat(result.getEarliestIntroduction().getTool()).isEqualTo(QualityGateTool.CODECOV);
            assertThat(result.getLatestIntroduction().getTool()).isEqualTo(QualityGateTool.CODECOV);
            assertThat(result.getToolIntroductions()).extracting(QGToolIntroduction::getTool)
                    .containsExactly(QualityGateTool.CODECOV, QualityGateTool.PMD);
        }

        @Test
        void exceptionDuringAnalysis_returnsErrorResult() {
            // getApiCallCount() is called once before the try block (fine) and once inside it,
            // to compute apiCallsUsed; throwing on the second call exercises the outer try/catch.
            when(github.getApiCallCount()).thenReturn(0).thenThrow(new RuntimeException("boom"));
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(0);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of());

            assertThat(result.getMetadata().isSuccess()).isFalse();
            assertThat(result.getMetadata().getErrorMessage()).isEqualTo("boom");
            assertThat(result.getToolIntroductions()).isEmpty();
        }

        @Test
        void getFirstCommitThrows_isCaughtAndReturnsNullRepoFirstCommit() {
            when(github.getFirstCommit(OWNER, REPO)).thenThrow(new RuntimeException("api down"));
            when(github.getCommitCount(OWNER, REPO)).thenReturn(0);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of());

            assertThat(result.getRepoFirstCommit()).isNull();
            assertThat(result.getMetadata().isSuccess()).isTrue();
        }

        @Test
        void getCommitCountThrows_returnsZero() {
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenThrow(new RuntimeException("api down"));

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of());

            assertThat(result.getTotalRepoCommits()).isZero();
        }

        @Test
        void blameThrowsException_isCaughtAndReturnsNull() {
            QualityGateDetection d = detection(QualityGateTool.PMD, ".github/workflows/ci.yml", List.of("keyword: pmd"));
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(0);
            when(github.getFileContentString(OWNER, REPO, ".github/workflows/ci.yml")).thenReturn("run: pmd");
            when(github.blameLineGraphQL(anyString(), anyString(), anyString(), anyInt()))
                    .thenThrow(new RuntimeException("graphql error"));
            when(prBasedFinder.findToolIntroduction(OWNER, REPO, QualityGateTool.PMD)).thenReturn(null);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(d));

            assertThat(result.getToolIntroductions().getFirst().getEffectiveIntroductionCommit()).isNull();
        }

        @Test
        void fileCreationThrowsException_isCaughtAndReturnsNull() {
            QualityGateDetection d = detection(QualityGateTool.CODECOV, "codecov.yml", List.of("codecov config"));
            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.empty());
            when(github.getCommitCount(OWNER, REPO)).thenReturn(0);
            when(github.fileExists(OWNER, REPO, "codecov.yml")).thenThrow(new RuntimeException("boom"));
            when(prBasedFinder.findToolIntroduction(OWNER, REPO, QualityGateTool.CODECOV)).thenReturn(null);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of(d));

            assertThat(result.getToolIntroductions().getFirst().getEffectiveIntroductionCommit()).isNull();
        }

        @Test
        void firstCommitWithMissingAuthorAndUnparseableDate_defaultsGracefully() {
            Map<String, Object> commitNoAuthor = new HashMap<>();
            commitNoAuthor.put("message", "init");
            commitNoAuthor.put("author", null);
            Map<String, Object> raw = new HashMap<>();
            raw.put("sha", "shashashasha");
            raw.put("commit", commitNoAuthor);

            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.of(raw));
            when(github.getCommitCount(OWNER, REPO)).thenReturn(1);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of());

            assertThat(result.getRepoFirstCommit().getAuthor()).isEqualTo("Unknown");
            assertThat(result.getRepoFirstCommit().getDate()).isNull();
            assertThat(result.getRepoFirstCommit().getShortSha()).isEqualTo("shashas");
        }

        @Test
        void firstCommitWithUnparseableDateString_dateIsNull() {
            Map<String, Object> author = new HashMap<>();
            author.put("name", "Alice");
            author.put("date", "not-a-date");
            Map<String, Object> commit = new HashMap<>();
            commit.put("author", author);
            commit.put("message", "init\nmore body text");
            Map<String, Object> raw = new HashMap<>();
            raw.put("sha", "abcdefghij");
            raw.put("commit", commit);

            when(github.getFirstCommit(OWNER, REPO)).thenReturn(Optional.of(raw));
            when(github.getCommitCount(OWNER, REPO)).thenReturn(1);

            QualityGateHistoryDetection result = analyzer.analyzeHistory(OWNER, REPO, List.of());

            assertThat(result.getRepoFirstCommit().getDate()).isNull();
            assertThat(result.getRepoFirstCommit().getMessage()).isEqualTo("init");
        }
    }

    @Nested
    class HelperMethods {

        private Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
            Method m = GitHistoryAnalyzerImpl.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(analyzer, args);
        }

        @Test
        void cleanKeyword_removesAllAnnotations() throws Exception {
            String result = (String) invoke("cleanKeyword", new Class[]{String.class},
                    "keyword: sonarcloud (enforcing) failsOnViolation=true failOnError=true triggers on PR (extra info)");
            assertThat(result).isEqualTo("sonarcloud");
        }

        @Test
        void cleanKeyword_nullInput_returnsEmptyString() throws Exception {
            assertThat((String) invoke("cleanKeyword", new Class[]{String.class}, (Object) null)).isEmpty();
        }

        @Test
        void isSearchableKeyword_tooShort_isFalse() throws Exception {
            assertThat((Boolean) invoke("isSearchableKeyword", new Class[]{String.class}, "ab")).isFalse();
        }

        @Test
        void isSearchableKeyword_genericTerm_isFalse() throws Exception {
            assertThat((Boolean) invoke("isSearchableKeyword", new Class[]{String.class}, "coverage")).isFalse();
        }

        @Test
        void isSearchableKeyword_specificTerm_isTrue() throws Exception {
            assertThat((Boolean) invoke("isSearchableKeyword", new Class[]{String.class}, "sonarcloud")).isTrue();
        }

        @Test
        void isWorkflowFile_variants() throws Exception {
            assertThat((Boolean) invoke("isWorkflowFile", new Class[]{String.class}, ".github/workflows/ci.yml")).isTrue();
            assertThat((Boolean) invoke("isWorkflowFile", new Class[]{String.class}, ".circleci/config.yml")).isTrue();
            assertThat((Boolean) invoke("isWorkflowFile", new Class[]{String.class}, "Jenkinsfile")).isTrue();
            assertThat((Boolean) invoke("isWorkflowFile", new Class[]{String.class}, ".travis.yml")).isTrue();
            assertThat((Boolean) invoke("isWorkflowFile", new Class[]{String.class}, "azure-pipelines.yml")).isTrue();
            assertThat((Boolean) invoke("isWorkflowFile", new Class[]{String.class}, "pom.xml")).isFalse();
        }

        @Test
        void isDedicatedConfigFile_nullPath_isFalse() throws Exception {
            assertThat((Boolean) invoke("isDedicatedConfigFile", new Class[]{String.class}, (Object) null)).isFalse();
        }

        @Test
        void isDedicatedConfigFile_withDirectoryPrefix_matchesByBaseName() throws Exception {
            assertThat((Boolean) invoke("isDedicatedConfigFile", new Class[]{String.class}, "some/dir/codecov.yml")).isTrue();
        }

        @Test
        void isDedicatedConfigFile_unrecognized_isFalse() throws Exception {
            assertThat((Boolean) invoke("isDedicatedConfigFile", new Class[]{String.class}, "random.txt")).isFalse();
        }

        @Test
        void extractSearchableKeywords_nullEvidence_fallsBackToToolPatterns() throws Exception {
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) invoke("extractSearchableKeywords",
                    new Class[]{List.class, QualityGateTool.class}, null, QualityGateTool.CHECKSTYLE);
            assertThat(result).isNotEmpty();
        }

        @Test
        void extractSearchableKeywords_mixOfGenericAndSpecific_keepsOnlySpecific() throws Exception {
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) invoke("extractSearchableKeywords",
                    new Class[]{List.class, QualityGateTool.class},
                    List.of("test", "keyword: sonarcloud"), QualityGateTool.SONARCLOUD);
            assertThat(result).containsExactly("sonarcloud");
        }
    }
}
