package com.thesis.qualitygateanalyzer.service.github;

import com.thesis.qualitygateanalyzer.domain.qualitygate.BranchProtection;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CommitInfo;
import com.thesis.qualitygateanalyzer.domain.qualitygate.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubApiClientImplTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private GitHubApiClientImpl client;

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";

    @BeforeEach
    void setUp() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        client = new GitHubApiClientImpl(builder, "test-token", 16_777_216);
    }

    private void mockResponse(HttpStatus status, String body) {
        ClientResponse response = ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(response));
    }

    private void mockNetworkError() {
        when(exchangeFunction.exchange(any())).thenReturn(Mono.error(new RuntimeException("network down")));
    }

    @Nested
    class Construction {
        @Test
        void withoutToken_stillBuildsWorkingClient() {
            WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
            GitHubApiClientImpl noTokenClient = new GitHubApiClientImpl(builder, "", 16_777_216);
            mockResponse(HttpStatus.OK, "{\"id\":1}");
            assertThat(noTokenClient.getRepository(OWNER, REPO)).isPresent();
        }

        @Test
        void blankToken_treatedAsNoToken() {
            WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
            assertThat(new GitHubApiClientImpl(builder, "   ", 1000)).isNotNull();
        }
    }

    @Nested
    class CallCounter {
        @Test
        void resetAndGetApiCallCount_trackCallsAcrossMethods() {
            client.resetCallCounter();
            assertThat(client.getApiCallCount()).isZero();
            mockResponse(HttpStatus.OK, "{}");
            client.getRepository(OWNER, REPO);
            assertThat(client.getApiCallCount()).isEqualTo(1);
            client.resetCallCounter();
            assertThat(client.getApiCallCount()).isZero();
        }
    }

    @Nested
    class GetRepository {
        @Test
        void success_returnsRepoData() {
            mockResponse(HttpStatus.OK, "{\"description\":\"A repo\",\"stargazers_count\":5}");
            Optional<Map<String, Object>> result = client.getRepository(OWNER, REPO);
            assertThat(result).isPresent();
            assertThat(result.get().get("description")).isEqualTo("A repo");
        }

        @Test
        void notFound_returnsEmpty() {
            mockResponse(HttpStatus.NOT_FOUND, "{}");
            assertThat(client.getRepository(OWNER, REPO)).isEmpty();
        }

        @Test
        void networkError_returnsEmpty() {
            mockNetworkError();
            assertThat(client.getRepository(OWNER, REPO)).isEmpty();
        }
    }

    @Nested
    class GetBranchProtection {
        @Test
        void newFormatChecksArray_withMatchedTool() {
            mockResponse(HttpStatus.OK, """
                    {
                      "required_status_checks": {"strict": true, "checks": [{"context": "sonarcloud", "app_id": 1}]},
                      "enforce_admins": {"enabled": true},
                      "allow_force_pushes": {"enabled": false},
                      "allow_deletions": {"enabled": true}
                    }
                    """);
            Optional<BranchProtection> result = client.getBranchProtection(OWNER, REPO, "main");
            assertThat(result).isPresent();
            BranchProtection bp = result.get();
            assertThat(bp.isProtected()).isTrue();
            assertThat(bp.isRequiresStatusChecks()).isTrue();
            assertThat(bp.isStrictStatusChecks()).isTrue();
            assertThat(bp.getRequiredChecks()).hasSize(1);
            assertThat(bp.isEnforceAdmins()).isTrue();
            assertThat(bp.isAllowForcePushes()).isFalse();
            assertThat(bp.isAllowDeletions()).isTrue();
        }

        @Test
        void oldFormatContextsArray_isParsed() {
            mockResponse(HttpStatus.OK, """
                    {"required_status_checks": {"strict": false, "contexts": ["codecov/project"]}}
                    """);
            BranchProtection bp = client.getBranchProtection(OWNER, REPO, "main").orElseThrow();
            assertThat(bp.getRequiredChecks()).hasSize(1);
            assertThat(bp.getRequiredChecks().getFirst().getContext()).isEqualTo("codecov/project");
        }

        @Test
        void noRequiredStatusChecks_flagIsFalse() {
            mockResponse(HttpStatus.OK, "{}");
            BranchProtection bp = client.getBranchProtection(OWNER, REPO, "main").orElseThrow();
            assertThat(bp.isRequiresStatusChecks()).isFalse();
            assertThat(bp.getRequiredChecks()).isEmpty();
        }

        @Test
        void notFound_returnsUnprotectedBranch() {
            mockResponse(HttpStatus.NOT_FOUND, "{}");
            BranchProtection bp = client.getBranchProtection(OWNER, REPO, "main").orElseThrow();
            assertThat(bp.isProtected()).isFalse();
            assertThat(bp.isRequiresStatusChecks()).isFalse();
        }

        @Test
        void forbidden_returnsEmpty() {
            mockResponse(HttpStatus.FORBIDDEN, "{}");
            assertThat(client.getBranchProtection(OWNER, REPO, "main")).isEmpty();
        }

        @Test
        void networkError_returnsEmpty() {
            mockNetworkError();
            assertThat(client.getBranchProtection(OWNER, REPO, "main")).isEmpty();
        }
    }

    @Nested
    class ListDirectoryAndFiles {
        @Test
        void listDirectory_returnsEntries() {
            mockResponse(HttpStatus.OK, "[{\"name\":\"ci.yml\"},{\"name\":\"README.md\"}]");
            List<Map<String, Object>> result = client.listDirectory(OWNER, REPO, ".github/workflows");
            assertThat(result).hasSize(2);
        }

        @Test
        void listDirectory_nonListResponse_returnsEmpty() {
            mockResponse(HttpStatus.OK, "{\"name\":\"single-file.txt\"}");
            assertThat(client.listDirectory(OWNER, REPO, "single-file.txt")).isEmpty();
        }

        @Test
        void listDirectory_notFound_returnsEmpty() {
            mockResponse(HttpStatus.NOT_FOUND, "{}");
            assertThat(client.listDirectory(OWNER, REPO, "missing")).isEmpty();
        }

        @Test
        void listDirectory_networkError_returnsEmpty() {
            mockNetworkError();
            assertThat(client.listDirectory(OWNER, REPO, "x")).isEmpty();
        }

        @Test
        void listWorkflowFiles_filtersYmlAndYamlOnly() {
            mockResponse(HttpStatus.OK,
                    "[{\"name\":\"ci.yml\"},{\"name\":\"release.yaml\"},{\"name\":\"README.md\"}]");
            List<String> result = client.listWorkflowFiles(OWNER, REPO);
            assertThat(result).containsExactlyInAnyOrder(
                    ".github/workflows/ci.yml", ".github/workflows/release.yaml");
        }
    }

    @Nested
    class GetFileContent {
        @Test
        void fileWithBase64Content_isDecoded() {
            String encoded = java.util.Base64.getEncoder().encodeToString("hello world".getBytes());
            mockResponse(HttpStatus.OK, "{\"type\":\"file\",\"encoding\":\"base64\",\"content\":\"" + encoded + "\"}");
            Optional<String> result = client.getFileContent(OWNER, REPO, "README.md");
            assertThat(result).contains("hello world");
        }

        @Test
        void directoryType_returnsEmpty() {
            mockResponse(HttpStatus.OK, "{\"type\":\"dir\"}");
            assertThat(client.getFileContent(OWNER, REPO, "src")).isEmpty();
        }

        @Test
        void nonBase64Encoding_returnsEmpty() {
            mockResponse(HttpStatus.OK, "{\"type\":\"file\",\"encoding\":\"none\",\"content\":\"raw\"}");
            assertThat(client.getFileContent(OWNER, REPO, "x")).isEmpty();
        }

        @Test
        void notFound_returnsEmpty() {
            mockResponse(HttpStatus.NOT_FOUND, "{}");
            assertThat(client.getFileContent(OWNER, REPO, "missing")).isEmpty();
        }

        @Test
        void networkError_returnsEmpty() {
            mockNetworkError();
            assertThat(client.getFileContent(OWNER, REPO, "x")).isEmpty();
        }
    }

    @Nested
    class FileExists {
        @Test
        void success_returnsTrue() {
            mockResponse(HttpStatus.OK, "{}");
            assertThat(client.fileExists(OWNER, REPO, "pom.xml")).isTrue();
        }

        @Test
        void notFound_returnsFalse() {
            mockResponse(HttpStatus.NOT_FOUND, "{}");
            assertThat(client.fileExists(OWNER, REPO, "missing")).isFalse();
        }

        @Test
        void networkError_returnsFalse() {
            mockNetworkError();
            assertThat(client.fileExists(OWNER, REPO, "x")).isFalse();
        }
    }

    @Nested
    class GetWorkflowRuns {
        @Test
        void success_returnsRunsLimited() {
            mockResponse(HttpStatus.OK, """
                    {"workflow_runs": [{"id":1},{"id":2},{"id":3}]}
                    """);
            List<Map<String, Object>> result = client.getWorkflowRuns(OWNER, REPO, ".github/workflows/ci.yml", 2);
            assertThat(result).hasSize(2);
        }

        @Test
        void missingKey_returnsEmpty() {
            mockResponse(HttpStatus.OK, "{}");
            assertThat(client.getWorkflowRuns(OWNER, REPO, "ci.yml", 10)).isEmpty();
        }

        @Test
        void notFound_returnsEmpty() {
            mockResponse(HttpStatus.NOT_FOUND, "{}");
            assertThat(client.getWorkflowRuns(OWNER, REPO, "ci.yml", 10)).isEmpty();
        }

        @Test
        void networkError_returnsEmpty() {
            mockNetworkError();
            assertThat(client.getWorkflowRuns(OWNER, REPO, "ci.yml", 10)).isEmpty();
        }
    }

    @Nested
    class ParseWorkflowRun {
        @Test
        void withPullRequests_extractsPrNumber() {
            Map<String, Object> raw = Map.of(
                    "id", 1, "name", "CI", "head_sha", "sha1", "conclusion", "success",
                    "status", "completed", "event", "pull_request",
                    "pull_requests", List.of(Map.of("number", 42)),
                    "created_at", "2024-01-01T00:00:00Z", "html_url", "http://x"
            );
            WorkflowRun run = client.parseWorkflowRun(raw, "ci.yml");
            assertThat(run.getPrNumber()).isEqualTo(42);
            assertThat(run.getId()).isEqualTo(1L);
        }

        @Test
        void withoutPullRequests_prNumberIsNull() {
            Map<String, Object> raw = Map.of("id", 1, "conclusion", "success", "status", "completed", "event", "push");
            WorkflowRun run = client.parseWorkflowRun(raw, "ci.yml");
            assertThat(run.getPrNumber()).isNull();
        }
    }

    @Nested
    class GetCheckRuns {
        @Test
        void singlePage_returnsAllRuns() {
            mockResponse(HttpStatus.OK, """
                    {"total_count": 2, "check_runs": [{"id":1},{"id":2}]}
                    """);
            List<Map<String, Object>> result = client.getCheckRuns(OWNER, REPO, "sha1");
            assertThat(result).hasSize(2);
        }

        @Test
        void emptyRuns_returnsEmpty() {
            mockResponse(HttpStatus.OK, "{\"total_count\": 0, \"check_runs\": []}");
            assertThat(client.getCheckRuns(OWNER, REPO, "sha1")).isEmpty();
        }

        @Test
        void networkError_returnsPartialResults() {
            mockNetworkError();
            assertThat(client.getCheckRuns(OWNER, REPO, "sha1")).isEmpty();
        }
    }

    @Nested
    class GetCommitStatuses {
        @Test
        void withStatuses_returnsThem() {
            mockResponse(HttpStatus.OK, "{\"statuses\": [{\"context\":\"codecov\"}]}");
            assertThat(client.getCommitStatuses(OWNER, REPO, "sha1")).hasSize(1);
        }

        @Test
        void missingStatusesKey_returnsEmpty() {
            mockResponse(HttpStatus.OK, "{}");
            assertThat(client.getCommitStatuses(OWNER, REPO, "sha1")).isEmpty();
        }

        @Test
        void networkError_returnsEmpty() {
            mockNetworkError();
            assertThat(client.getCommitStatuses(OWNER, REPO, "sha1")).isEmpty();
        }
    }

    @Nested
    class GetRecentClosedPRs {
        @Test
        void success_returnsLimitedList() {
            mockResponse(HttpStatus.OK, "[{\"number\":1},{\"number\":2},{\"number\":3}]");
            assertThat(client.getRecentClosedPRs(OWNER, REPO, 2)).hasSize(2);
        }

        @Test
        void networkError_returnsEmpty() {
            mockNetworkError();
            assertThat(client.getRecentClosedPRs(OWNER, REPO, 10)).isEmpty();
        }
    }

    @Nested
    class GetPullRequest {
        @Test
        void success_returnsData() {
            mockResponse(HttpStatus.OK, "{\"number\":5,\"state\":\"open\"}");
            assertThat(client.getPullRequest(OWNER, REPO, 5)).isPresent();
        }

        @Test
        void notFound_returnsEmpty() {
            mockResponse(HttpStatus.NOT_FOUND, "{}");
            assertThat(client.getPullRequest(OWNER, REPO, 5)).isEmpty();
        }

        @Test
        void networkError_returnsEmpty() {
            mockNetworkError();
            assertThat(client.getPullRequest(OWNER, REPO, 5)).isEmpty();
        }
    }

    @Nested
    class GetRemainingRateLimit {
        @Test
        void success_returnsRemaining() {
            mockResponse(HttpStatus.OK, "{\"resources\": {\"core\": {\"remaining\": 4999}}}");
            assertThat(client.getRemainingRateLimit()).isEqualTo(4999);
        }

        @Test
        void networkError_returnsMinusOne() {
            mockNetworkError();
            assertThat(client.getRemainingRateLimit()).isEqualTo(-1);
        }
    }

    @Nested
    class GetCommitsForPath {
        @Test
        void success_returnsCommits() {
            mockResponse(HttpStatus.OK, "[{\"sha\":\"a\"},{\"sha\":\"b\"}]");
            assertThat(client.getCommitsForPath(OWNER, REPO, "pom.xml", 10)).hasSize(2);
        }

        @Test
        void notFound_returnsEmpty() {
            mockResponse(HttpStatus.NOT_FOUND, "{}");
            assertThat(client.getCommitsForPath(OWNER, REPO, "pom.xml", 10)).isEmpty();
        }

        @Test
        void networkError_returnsEmpty() {
            mockNetworkError();
            assertThat(client.getCommitsForPath(OWNER, REPO, "pom.xml", 10)).isEmpty();
        }
    }

    @Nested
    class GetFirstCommit {
        @Test
        void nonEmpty_returnsFirst() {
            mockResponse(HttpStatus.OK, "[{\"sha\":\"first-sha\"}]");
            Optional<Map<String, Object>> result = client.getFirstCommit(OWNER, REPO);
            assertThat(result).isPresent();
            assertThat(result.get().get("sha")).isEqualTo("first-sha");
        }

        @Test
        void empty_returnsEmptyOptional() {
            mockResponse(HttpStatus.OK, "[]");
            assertThat(client.getFirstCommit(OWNER, REPO)).isEmpty();
        }

        @Test
        void networkError_returnsEmptyOptional() {
            mockNetworkError();
            assertThat(client.getFirstCommit(OWNER, REPO)).isEmpty();
        }
    }

    @Nested
    class GetAllCommits {
        @Test
        void singlePartialPage_stopsAfterFirstPage() {
            mockResponse(HttpStatus.OK, "[{\"sha\":\"a\"},{\"sha\":\"b\"}]");
            assertThat(client.getAllCommits(OWNER, REPO)).hasSize(2);
        }

        @Test
        void emptyFirstPage_returnsEmpty() {
            mockResponse(HttpStatus.OK, "[]");
            assertThat(client.getAllCommits(OWNER, REPO)).isEmpty();
        }

        @Test
        void notFound_returnsEmptyWithoutThrowing() {
            mockResponse(HttpStatus.NOT_FOUND, "{}");
            assertThat(client.getAllCommits(OWNER, REPO)).isEmpty();
        }

        @Test
        void networkError_throwsRuntimeException() {
            mockNetworkError();
            assertThrows(RuntimeException.class, () -> client.getAllCommits(OWNER, REPO));
        }
    }

    @Nested
    class GetCommitCount {
        @Test
        void success_sumsContributions() {
            mockResponse(HttpStatus.OK, "[{\"contributions\":5},{\"contributions\":10}]");
            assertThat(client.getCommitCount(OWNER, REPO)).isEqualTo(15);
        }

        @Test
        void networkError_returnsZero() {
            mockNetworkError();
            assertThat(client.getCommitCount(OWNER, REPO)).isZero();
        }
    }

    @Nested
    class GetFileContentAtRef {
        @Test
        void base64Content_isDecodedAndNewlinesStripped() {
            String encoded = java.util.Base64.getEncoder().encodeToString("line one".getBytes());
            // Use an escaped "\n" (literal backslash-n) so the JSON stays valid; the app strips
            // the resulting newline character from the decoded string value before base64-decoding.
            String withNewlines = encoded.substring(0, encoded.length() / 2) + "\\n" + encoded.substring(encoded.length() / 2);
            mockResponse(HttpStatus.OK, "{\"content\":\"" + withNewlines + "\",\"encoding\":\"base64\"}");
            String result = client.getFileContentAtRef(OWNER, REPO, "a.txt", "main");
            assertThat(result).isEqualTo("line one");
        }

        @Test
        void encodingDefaultsToBase64WhenMissing() {
            String encoded = java.util.Base64.getEncoder().encodeToString("data".getBytes());
            mockResponse(HttpStatus.OK, "{\"content\":\"" + encoded + "\"}");
            assertThat(client.getFileContentAtRef(OWNER, REPO, "a.txt", "main")).isEqualTo("data");
        }

        @Test
        void noContentKey_returnsNull() {
            mockResponse(HttpStatus.OK, "{}");
            assertThat(client.getFileContentAtRef(OWNER, REPO, "a.txt", "main")).isNull();
        }

        @Test
        void notFound_returnsNull() {
            mockResponse(HttpStatus.NOT_FOUND, "{}");
            assertThat(client.getFileContentAtRef(OWNER, REPO, "a.txt", "main")).isNull();
        }

        @Test
        void networkError_returnsNull() {
            mockNetworkError();
            assertThat(client.getFileContentAtRef(OWNER, REPO, "a.txt", "main")).isNull();
        }

        @Test
        void getFileContentString_delegatesWithHeadRef() {
            String encoded = java.util.Base64.getEncoder().encodeToString("head content".getBytes());
            mockResponse(HttpStatus.OK, "{\"content\":\"" + encoded + "\",\"encoding\":\"base64\"}");
            assertThat(client.getFileContentString(OWNER, REPO, "a.txt")).isEqualTo("head content");
        }
    }

    @Nested
    class GetFileCreationCommit {
        @Test
        void returnsOldestCommitParsed() {
            mockResponse(HttpStatus.OK, """
                    [
                      {"sha":"newest", "commit": {"author": {"name":"Bob","date":"2024-02-01T00:00:00Z"}, "message":"latest"}},
                      {"sha":"oldest", "commit": {"author": {"name":"Alice","date":"2023-01-01T00:00:00Z"}, "message":"first\\nmore body"}}
                    ]
                    """);
            CommitInfo result = client.getFileCreationCommit(OWNER, REPO, "pom.xml");
            assertThat(result.getSha()).isEqualTo("oldest");
            assertThat(result.getAuthor()).isEqualTo("Alice");
            assertThat(result.getMessage()).isEqualTo("first");
            assertThat(result.getShortSha()).isEqualTo("oldest");
        }

        @Test
        void noCommits_returnsNull() {
            mockResponse(HttpStatus.OK, "[]");
            assertThat(client.getFileCreationCommit(OWNER, REPO, "pom.xml")).isNull();
        }

        @Test
        void missingCommitField_returnsNull() {
            mockResponse(HttpStatus.OK, "[{\"sha\":\"a\"}]");
            assertThat(client.getFileCreationCommit(OWNER, REPO, "pom.xml")).isNull();
        }
    }

    @Nested
    class BlameLineGraphQL {
        @Test
        void matchingRange_returnsCommitInfo() {
            mockResponse(HttpStatus.OK, """
                    {"data": {"repository": {"defaultBranchRef": {"target": {"blame": {"ranges": [
                        {"startingLine":1, "endingLine":10, "commit": {"oid":"abcdef1234567", "message":"init\\nbody",
                         "committedDate":"2024-01-01T00:00:00Z", "author": {"name":"Alice"}}}
                    ]}}}}}}
                    """);
            CommitInfo result = client.blameLineGraphQL(OWNER, REPO, "pom.xml", 5);
            assertThat(result.getSha()).isEqualTo("abcdef1234567");
            assertThat(result.getShortSha()).isEqualTo("abcdef1");
            assertThat(result.getAuthor()).isEqualTo("Alice");
            assertThat(result.getMessage()).isEqualTo("init");
        }

        @Test
        void lineOutsideAllRanges_returnsNull() {
            mockResponse(HttpStatus.OK, """
                    {"data": {"repository": {"defaultBranchRef": {"target": {"blame": {"ranges": [
                        {"startingLine":1, "endingLine":10, "commit": {"oid":"a", "committedDate":"2024-01-01T00:00:00Z"}}
                    ]}}}}}}
                    """);
            assertThat(client.blameLineGraphQL(OWNER, REPO, "pom.xml", 50)).isNull();
        }

        @Test
        void emptyRanges_returnsNull() {
            mockResponse(HttpStatus.OK, """
                    {"data": {"repository": {"defaultBranchRef": {"target": {"blame": {"ranges": []}}}}}}
                    """);
            assertThat(client.blameLineGraphQL(OWNER, REPO, "pom.xml", 1)).isNull();
        }

        @Test
        void noBlameField_returnsNull() {
            mockResponse(HttpStatus.OK, "{\"data\": {\"repository\": {\"defaultBranchRef\": {\"target\": {}}}}}");
            assertThat(client.blameLineGraphQL(OWNER, REPO, "pom.xml", 1)).isNull();
        }

        @Test
        void noTarget_returnsNull() {
            mockResponse(HttpStatus.OK, "{\"data\": {\"repository\": {\"defaultBranchRef\": {}}}}");
            assertThat(client.blameLineGraphQL(OWNER, REPO, "pom.xml", 1)).isNull();
        }

        @Test
        void noDefaultBranchRef_returnsNull() {
            mockResponse(HttpStatus.OK, "{\"data\": {\"repository\": {}}}");
            assertThat(client.blameLineGraphQL(OWNER, REPO, "pom.xml", 1)).isNull();
        }

        @Test
        void noRepository_returnsNull() {
            mockResponse(HttpStatus.OK, "{\"data\": {}}");
            assertThat(client.blameLineGraphQL(OWNER, REPO, "pom.xml", 1)).isNull();
        }

        @Test
        void noDataWithErrors_returnsNull() {
            mockResponse(HttpStatus.OK, "{\"errors\": [{\"message\":\"bad query\"}]}");
            assertThat(client.blameLineGraphQL(OWNER, REPO, "pom.xml", 1)).isNull();
        }

        @Test
        void networkError_returnsNull() {
            mockNetworkError();
            assertThat(client.blameLineGraphQL(OWNER, REPO, "pom.xml", 1)).isNull();
        }
    }

    @Nested
    class PrBasedSearchHelpers {
        @Test
        void getLatestPRNumber_success() {
            mockResponse(HttpStatus.OK, "[{\"number\":99}]");
            assertThat(client.getLatestPRNumber(OWNER, REPO)).isEqualTo(99);
        }

        @Test
        void getLatestPRNumber_noPRs_returnsZero() {
            mockResponse(HttpStatus.OK, "[]");
            assertThat(client.getLatestPRNumber(OWNER, REPO)).isZero();
        }

        @Test
        void getLatestPRNumber_networkError_returnsZero() {
            mockNetworkError();
            assertThat(client.getLatestPRNumber(OWNER, REPO)).isZero();
        }

        @Test
        void getFirstPRNumber_success() {
            mockResponse(HttpStatus.OK, "[{\"number\":1}]");
            assertThat(client.getFirstPRNumber(OWNER, REPO)).isEqualTo(1);
        }

        @Test
        void getFirstPRNumber_noPRs_returnsOne() {
            mockResponse(HttpStatus.OK, "[]");
            assertThat(client.getFirstPRNumber(OWNER, REPO)).isEqualTo(1);
        }

        @Test
        void getFirstPRNumber_networkError_returnsOne() {
            mockNetworkError();
            assertThat(client.getFirstPRNumber(OWNER, REPO)).isEqualTo(1);
        }

        @Test
        void getPRIfExists_success() {
            mockResponse(HttpStatus.OK, "{\"number\":5}");
            assertThat(client.getPRIfExists(OWNER, REPO, 5)).isNotNull();
        }

        @Test
        void getPRIfExists_notFound_returnsNull() {
            mockResponse(HttpStatus.NOT_FOUND, "{}");
            assertThat(client.getPRIfExists(OWNER, REPO, 5)).isNull();
        }

        @Test
        void getPRIfExists_networkError_returnsNull() {
            mockNetworkError();
            assertThat(client.getPRIfExists(OWNER, REPO, 5)).isNull();
        }

        @Test
        void getTotalPRCount_success() {
            mockResponse(HttpStatus.OK, "{\"total_count\": 42}");
            assertThat(client.getTotalPRCount(OWNER, REPO)).isEqualTo(42);
        }

        @Test
        void getTotalPRCount_missingKey_returnsZero() {
            mockResponse(HttpStatus.OK, "{}");
            assertThat(client.getTotalPRCount(OWNER, REPO)).isZero();
        }

        @Test
        void getTotalPRCount_networkError_returnsZero() {
            mockNetworkError();
            assertThat(client.getTotalPRCount(OWNER, REPO)).isZero();
        }
    }
}
