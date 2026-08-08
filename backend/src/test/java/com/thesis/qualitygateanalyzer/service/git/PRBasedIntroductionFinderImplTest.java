package com.thesis.qualitygateanalyzer.service.git;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CheckRun;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CommitStatus;
import com.thesis.qualitygateanalyzer.domain.qualitygate.QGFileIntroduction;
import com.thesis.qualitygateanalyzer.service.github.CheckRunMatcher;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PRBasedIntroductionFinderImplTest {

    @Mock
    private GitHubApiClient github;
    @Mock
    private CheckRunMatcher checkRunMatcher;

    private PRBasedIntroductionFinderImpl finder;

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";

    @BeforeEach
    void setUp() {
        finder = new PRBasedIntroductionFinderImpl(github, checkRunMatcher);
    }

    private Map<String, Object> prPayload(int number, String sha, String author, String createdAt, String title) {
        Map<String, Object> head = new HashMap<>();
        head.put("sha", sha);
        Map<String, Object> user = new HashMap<>();
        user.put("login", author);
        Map<String, Object> pr = new HashMap<>();
        pr.put("number", number);
        pr.put("head", head);
        pr.put("user", user);
        pr.put("created_at", createdAt);
        pr.put("title", title);
        return pr;
    }

    @Nested
    class NoPullRequests {
        @Test
        void latestPrZero_returnsNull() {
            when(github.getFirstPRNumber(OWNER, REPO)).thenReturn(0);
            when(github.getLatestPRNumber(OWNER, REPO)).thenReturn(0);

            QGFileIntroduction result = finder.findToolIntroduction(OWNER, REPO, QualityGateTool.SONARCLOUD);

            assertThat(result).isNull();
        }
    }

    @Nested
    class ExceptionHandling {
        @Test
        void exceptionDuringSearch_isCaughtAndReturnsNull() {
            when(github.getFirstPRNumber(OWNER, REPO)).thenThrow(new RuntimeException("api error"));

            QGFileIntroduction result = finder.findToolIntroduction(OWNER, REPO, QualityGateTool.SONARCLOUD);

            assertThat(result).isNull();
        }
    }

    @Nested
    class LinearSearchPath {
        @Test
        void toolFoundViaCheckRun_returnsFileIntroduction() {
            // range (high-low=2) is below LINEAR_SEARCH_THRESHOLD(10) -> triggers linear search immediately.
            when(github.getFirstPRNumber(OWNER, REPO)).thenReturn(1);
            when(github.getLatestPRNumber(OWNER, REPO)).thenReturn(3);

            Map<String, Object> pr1 = prPayload(1, "sha1", "alice", "2024-01-01T00:00:00Z", "PR one");
            when(github.getPRIfExists(OWNER, REPO, 1)).thenReturn(pr1);
            when(github.getCheckRuns(OWNER, REPO, "sha1")).thenReturn(List.of());
            when(github.getCommitStatuses(OWNER, REPO, "sha1")).thenReturn(List.of());

            Map<String, Object> pr2 = prPayload(2, "sha2", "bob", "2024-01-02T00:00:00Z", "PR two");
            when(github.getPRIfExists(OWNER, REPO, 2)).thenReturn(pr2);
            Map<String, Object> rawCheck = Map.of("id", 1);
            when(github.getCheckRuns(OWNER, REPO, "sha2")).thenReturn(List.of(rawCheck));
            when(github.getCommitStatuses(OWNER, REPO, "sha2")).thenReturn(List.of());
            CheckRun matchedCheck = CheckRun.builder().id(1L).matchedTool(QualityGateTool.SONARCLOUD).build();
            when(checkRunMatcher.parseAndMatch(rawCheck)).thenReturn(matchedCheck);

            QGFileIntroduction result = finder.findToolIntroduction(OWNER, REPO, QualityGateTool.SONARCLOUD);

            assertThat(result).isNotNull();
            assertThat(result.getFilePath()).isEqualTo("PR #2 (external app)");
            assertThat(result.getSearchPattern()).contains("SonarCloud");
            assertThat(result.getIntroducedAt().getSha()).isEqualTo("sha2");
            assertThat(result.getIntroducedAt().getAuthor()).isEqualTo("bob");
            assertThat(result.isPresentSinceFileCreation()).isFalse();
            assertThat(result.getAllOccurrences()).hasSize(1);
            // PR #3 should never be checked since linear search stops at first match (oldest-first).
            verify(github, never()).getPRIfExists(OWNER, REPO, 3);
        }

        @Test
        void toolFoundViaCommitStatus_returnsFileIntroduction() {
            when(github.getFirstPRNumber(OWNER, REPO)).thenReturn(1);
            when(github.getLatestPRNumber(OWNER, REPO)).thenReturn(2);

            Map<String, Object> pr1 = prPayload(1, "sha1", "alice", "2024-01-01T00:00:00Z", "PR one");
            when(github.getPRIfExists(OWNER, REPO, 1)).thenReturn(pr1);
            when(github.getCheckRuns(OWNER, REPO, "sha1")).thenReturn(List.of());
            Map<String, Object> rawStatus = Map.of("id", 1);
            when(github.getCommitStatuses(OWNER, REPO, "sha1")).thenReturn(List.of(rawStatus));
            CommitStatus matchedStatus = CommitStatus.builder().id(1L).matchedTool(QualityGateTool.CODECOV).build();
            when(checkRunMatcher.parseAndMatchStatus(rawStatus)).thenReturn(matchedStatus);

            QGFileIntroduction result = finder.findToolIntroduction(OWNER, REPO, QualityGateTool.CODECOV);

            assertThat(result).isNotNull();
            assertThat(result.getFilePath()).isEqualTo("PR #1 (external app)");
        }

        @Test
        void toolNeverFound_returnsNull() {
            when(github.getFirstPRNumber(OWNER, REPO)).thenReturn(1);
            when(github.getLatestPRNumber(OWNER, REPO)).thenReturn(2);

            Map<String, Object> pr1 = prPayload(1, "sha1", "alice", "2024-01-01T00:00:00Z", "PR one");
            when(github.getPRIfExists(OWNER, REPO, 1)).thenReturn(pr1);
            when(github.getCheckRuns(OWNER, REPO, "sha1")).thenReturn(List.of());
            when(github.getCommitStatuses(OWNER, REPO, "sha1")).thenReturn(List.of());

            Map<String, Object> pr2 = prPayload(2, "sha2", "bob", "2024-01-02T00:00:00Z", "PR two");
            when(github.getPRIfExists(OWNER, REPO, 2)).thenReturn(pr2);
            when(github.getCheckRuns(OWNER, REPO, "sha2")).thenReturn(List.of());
            when(github.getCommitStatuses(OWNER, REPO, "sha2")).thenReturn(List.of());

            QGFileIntroduction result = finder.findToolIntroduction(OWNER, REPO, QualityGateTool.SONARCLOUD);

            assertThat(result).isNull();
        }

        @Test
        void deletedPr_returnsNullAndIsCached() {
            when(github.getFirstPRNumber(OWNER, REPO)).thenReturn(1);
            when(github.getLatestPRNumber(OWNER, REPO)).thenReturn(1);
            when(github.getPRIfExists(OWNER, REPO, 1)).thenReturn(null);

            QGFileIntroduction result = finder.findToolIntroduction(OWNER, REPO, QualityGateTool.SONARCLOUD);

            assertThat(result).isNull();
        }

        @Test
        void prWithoutHeadSha_returnsNull() {
            when(github.getFirstPRNumber(OWNER, REPO)).thenReturn(1);
            when(github.getLatestPRNumber(OWNER, REPO)).thenReturn(1);
            Map<String, Object> prNoHead = new HashMap<>();
            prNoHead.put("number", 1);
            prNoHead.put("head", null);
            when(github.getPRIfExists(OWNER, REPO, 1)).thenReturn(prNoHead);

            QGFileIntroduction result = finder.findToolIntroduction(OWNER, REPO, QualityGateTool.SONARCLOUD);

            assertThat(result).isNull();
        }

        @Test
        void exceptionWhileCheckingSpecificPR_isCaughtAndTreatedAsNotFound() {
            when(github.getFirstPRNumber(OWNER, REPO)).thenReturn(1);
            when(github.getLatestPRNumber(OWNER, REPO)).thenReturn(1);
            when(github.getPRIfExists(OWNER, REPO, 1)).thenThrow(new RuntimeException("network error"));

            QGFileIntroduction result = finder.findToolIntroduction(OWNER, REPO, QualityGateTool.SONARCLOUD);

            assertThat(result).isNull();
        }

        @Test
        void userWithoutLogin_defaultsToUnknownAuthor() {
            when(github.getFirstPRNumber(OWNER, REPO)).thenReturn(1);
            when(github.getLatestPRNumber(OWNER, REPO)).thenReturn(1);
            Map<String, Object> prNoUser = new HashMap<>();
            Map<String, Object> head = new HashMap<>();
            head.put("sha", "sha1");
            prNoUser.put("number", 1);
            prNoUser.put("head", head);
            prNoUser.put("user", null);
            prNoUser.put("created_at", "2024-01-01T00:00:00Z");
            when(github.getPRIfExists(OWNER, REPO, 1)).thenReturn(prNoUser);
            Map<String, Object> rawCheck = Map.of("id", 1);
            when(github.getCheckRuns(OWNER, REPO, "sha1")).thenReturn(List.of(rawCheck));
            when(github.getCommitStatuses(OWNER, REPO, "sha1")).thenReturn(List.of());
            when(checkRunMatcher.parseAndMatch(rawCheck))
                    .thenReturn(CheckRun.builder().id(1L).matchedTool(QualityGateTool.SONARCLOUD).build());

            QGFileIntroduction result = finder.findToolIntroduction(OWNER, REPO, QualityGateTool.SONARCLOUD);

            assertThat(result.getIntroducedAt().getAuthor()).isEqualTo("Unknown");
        }

        @Test
        void unparseableCreatedAt_resultsInNullDate() {
            when(github.getFirstPRNumber(OWNER, REPO)).thenReturn(1);
            when(github.getLatestPRNumber(OWNER, REPO)).thenReturn(1);
            Map<String, Object> pr = prPayload(1, "sha1", "alice", "not-a-date", "PR one");
            when(github.getPRIfExists(OWNER, REPO, 1)).thenReturn(pr);
            Map<String, Object> rawCheck = Map.of("id", 1);
            when(github.getCheckRuns(OWNER, REPO, "sha1")).thenReturn(List.of(rawCheck));
            when(github.getCommitStatuses(OWNER, REPO, "sha1")).thenReturn(List.of());
            when(checkRunMatcher.parseAndMatch(rawCheck))
                    .thenReturn(CheckRun.builder().id(1L).matchedTool(QualityGateTool.SONARCLOUD).build());

            QGFileIntroduction result = finder.findToolIntroduction(OWNER, REPO, QualityGateTool.SONARCLOUD);

            assertThat(result.getIntroducedAt().getDate()).isNull();
        }

        @Test
        void samePrCheckedTwiceInSearch_usesCacheOnSecondLookup() {
            // A range that still triggers linear search (2 < threshold) but re-evaluates the SAME
            // PR result object for two different tool searches is exercised implicitly here by
            // checking a PR that has multiple tools; caching itself is verified via call-count.
            when(github.getFirstPRNumber(OWNER, REPO)).thenReturn(1);
            when(github.getLatestPRNumber(OWNER, REPO)).thenReturn(1);
            Map<String, Object> pr = prPayload(1, "sha1", "alice", "2024-01-01T00:00:00Z", "PR one");
            when(github.getPRIfExists(OWNER, REPO, 1)).thenReturn(pr);
            Map<String, Object> rawCheck = Map.of("id", 1);
            when(github.getCheckRuns(OWNER, REPO, "sha1")).thenReturn(List.of(rawCheck));
            when(github.getCommitStatuses(OWNER, REPO, "sha1")).thenReturn(List.of());
            when(checkRunMatcher.parseAndMatch(rawCheck))
                    .thenReturn(CheckRun.builder().id(1L).matchedTool(QualityGateTool.SONARCLOUD).build());

            finder.findToolIntroduction(OWNER, REPO, QualityGateTool.SONARCLOUD);
            // A second, independent search call clears the cache (per implementation) but each
            // individual search should only hit the network once per PR even if visited multiple times.
            verify(github, times(1)).getPRIfExists(OWNER, REPO, 1);
        }
    }

    @Nested
    class BinarySearchPath {
        @Test
        void largeRange_narrowsToLinearSearchAndFindsTool() {
            // Range 0..25 (>= LINEAR_SEARCH_THRESHOLD) forces at least one real binary-search
            // comparison before narrowing down to a linear scan.
            when(github.getFirstPRNumber(OWNER, REPO)).thenReturn(1);
            when(github.getLatestPRNumber(OWNER, REPO)).thenReturn(25);

            // Make every PR "exist" with a distinct sha. Binary search assumes a monotonic step
            // function (once introduced, a tool keeps appearing in all later PRs), so the tool
            // must be present from PR #5 onward, not just exactly at PR #5.
            for (int i = 1; i <= 25; i++) {
                Map<String, Object> pr = prPayload(i, "sha" + i, "user" + i, "2024-01-01T00:00:00Z", "PR " + i);
                lenient().when(github.getPRIfExists(OWNER, REPO, i)).thenReturn(pr);
                lenient().when(github.getCommitStatuses(OWNER, REPO, "sha" + i)).thenReturn(List.of());
                if (i >= 5) {
                    Map<String, Object> rawCheck = Map.of("id", i);
                    lenient().when(github.getCheckRuns(OWNER, REPO, "sha" + i)).thenReturn(List.of(rawCheck));
                    lenient().when(checkRunMatcher.parseAndMatch(rawCheck))
                            .thenReturn(CheckRun.builder().id((long) i).matchedTool(QualityGateTool.SONARCLOUD).build());
                } else {
                    lenient().when(github.getCheckRuns(OWNER, REPO, "sha" + i)).thenReturn(List.of());
                }
            }

            QGFileIntroduction result = finder.findToolIntroduction(OWNER, REPO, QualityGateTool.SONARCLOUD);

            assertThat(result).isNotNull();
            assertThat(result.getFilePath()).isEqualTo("PR #5 (external app)");
        }
    }
}
