package com.thesis.qualitygateanalyzer.service.github;

import com.thesis.qualitygateanalyzer.constant.GitHubConstants;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.BranchProtection;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CommitInfo;
import com.thesis.qualitygateanalyzer.domain.qualitygate.WorkflowRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default {@link GitHubApiClient} implementation backed by Spring WebClient.
 * Provides all endpoints required for repository inspection, workflow run retrieval,
 * check run and commit status fetching, and git history analysis via blame.
 */
@Slf4j
@Component
public class GitHubApiClientImpl implements GitHubApiClient {

    private final WebClient webClient;
    private final AtomicInteger apiCallCounter = new AtomicInteger(0);

    public GitHubApiClientImpl(
            WebClient.Builder builder,
            @Value("${github.api.token:}") String token,
            @Value("${webclient.max-buffer-size:16777216}") int maxBufferSize) {

        WebClient.Builder b = builder
                .baseUrl(GitHubConstants.BASE_URL)
                .defaultHeader(HttpHeaders.ACCEPT, GitHubConstants.ACCEPT_HEADER_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, GitHubConstants.USER_AGENT)
                .codecs(config -> config.defaultCodecs().maxInMemorySize(maxBufferSize));

        if (token != null && !token.isBlank()) {
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            log.info("GitHub client initialized with authentication token (max buffer: {} bytes)", maxBufferSize);
        } else {
            log.warn("GitHub client initialized without authentication token - rate limit is 60 requests/hour");
        }

        this.webClient = b.build();
    }

    @Override
    public void resetCallCounter() {
        apiCallCounter.set(0);
    }

    @Override
    public int getApiCallCount() {
        return apiCallCounter.get();
    }

    // REPOSITORY

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getRepository(String owner, String repo) {
        try {
            apiCallCounter.incrementAndGet();
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}", owner, repo)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();
            return Optional.ofNullable(response);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching repository: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // BRANCH PROTECTION

    @Override
    @SuppressWarnings("unchecked")
    public Optional<BranchProtection> getBranchProtection(String owner, String repo, String branch) {
        try {
            apiCallCounter.incrementAndGet();
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/branches/{branch}/protection", owner, repo, branch)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(parseBranchProtection(branch, response));

        } catch (WebClientResponseException.NotFound e) {
            // Branch not protected - this is a valid state
            log.debug("Branch {} is not protected", branch);
            return Optional.of(BranchProtection.builder()
                    .branch(branch)
                    .isProtected(false)
                    .requiresStatusChecks(false)
                    .requiredChecks(Collections.emptyList())
                    .build());
        } catch (WebClientResponseException.Forbidden e) {
            // API access forbidden - expected for public repos without admin access
            // This is normal behavior, not a warning
            log.debug("Branch protection API requires admin access for {}/{} branch {}",
                    owner, repo, branch);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching branch protection for {} branch {}: {}",
                    owner + "/" + repo, branch, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parse branch protection API response into our model.
     */
    @SuppressWarnings("unchecked")
    private BranchProtection parseBranchProtection(String branch, Map<String, Object> response) {
        List<BranchProtection.RequiredCheck> requiredChecks = new ArrayList<>();
        boolean requiresStatusChecks = false;
        boolean strictStatusChecks = false;

        // Parse required_status_checks
        Map<String, Object> statusChecks = (Map<String, Object>) response.get("required_status_checks");
        if (statusChecks != null) {
            requiresStatusChecks = true;
            strictStatusChecks = Boolean.TRUE.equals(statusChecks.get("strict"));

            // Try new format first (checks array)
            List<Map<String, Object>> checks = (List<Map<String, Object>>) statusChecks.get("checks");
            if (checks != null && !checks.isEmpty()) {
                for (Map<String, Object> check : checks) {
                    String context = (String) check.get("context");
                    Integer appId = check.get("app_id") != null ?
                            ((Number) check.get("app_id")).intValue() : null;

                    // Try to match to a QG tool
                    QualityGateTool matchedTool = matchCheckToTool(context, null);
                    String appSlug = matchedTool != null ?
                            matchedTool.getCheckRunAppSlugs().stream().findFirst().orElse(null) : null;

                    requiredChecks.add(BranchProtection.RequiredCheck.builder()
                            .context(context)
                            .appId(appId)
                            .appSlug(appSlug)
                            .matchedTool(matchedTool)
                            .build());
                }
            } else {
                // Fall back to old format (contexts array)
                List<String> contexts = (List<String>) statusChecks.get("contexts");
                if (contexts != null) {
                    for (String context : contexts) {
                        QualityGateTool matchedTool = matchCheckToTool(context, null);
                        requiredChecks.add(BranchProtection.RequiredCheck.builder()
                                .context(context)
                                .matchedTool(matchedTool)
                                .build());
                    }
                }
            }
        }

        // Parse enforce_admins
        boolean enforceAdmins = false;
        Map<String, Object> enforceAdminsObj = (Map<String, Object>) response.get("enforce_admins");
        if (enforceAdminsObj != null) {
            enforceAdmins = Boolean.TRUE.equals(enforceAdminsObj.get("enabled"));
        }

        // Parse allow_force_pushes
        boolean allowForcePushes = false;
        Map<String, Object> forcePushObj = (Map<String, Object>) response.get("allow_force_pushes");
        if (forcePushObj != null) {
            allowForcePushes = Boolean.TRUE.equals(forcePushObj.get("enabled"));
        }

        // Parse allow_deletions
        boolean allowDeletions = false;
        Map<String, Object> deletionsObj = (Map<String, Object>) response.get("allow_deletions");
        if (deletionsObj != null) {
            allowDeletions = Boolean.TRUE.equals(deletionsObj.get("enabled"));
        }

        return BranchProtection.builder()
                .branch(branch)
                .isProtected(true)
                .requiresStatusChecks(requiresStatusChecks)
                .strictStatusChecks(strictStatusChecks)
                .requiredChecks(requiredChecks)
                .enforceAdmins(enforceAdmins)
                .allowForcePushes(allowForcePushes)
                .allowDeletions(allowDeletions)
                .build();
    }

    /**
     * Try to match a check context/name to a QG tool.
     */
    private QualityGateTool matchCheckToTool(String context, String appSlug) {
        if (context == null && appSlug == null) {
            return null;
        }

        // Try app slug first (most reliable)
        if (appSlug != null) {
            Optional<QualityGateTool> match = QualityGateTool.matchAppSlug(appSlug);
            if (match.isPresent()) {
                return match.get();
            }
        }

        // Try context/name matching
        if (context != null) {
            Optional<QualityGateTool> match = QualityGateTool.matchCheckRunName(context);
            if (match.isPresent()) {
                return match.get();
            }
        }

        return null;
    }

    // FILE CONTENTS

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listDirectory(String owner, String repo, String path) {
        try {
            apiCallCounter.incrementAndGet();
            Object response = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (response instanceof List) {
                return (List<Map<String, Object>>) response;
            }
            return Collections.emptyList();
        } catch (WebClientResponseException.NotFound e) {
            return Collections.emptyList();
        } catch (Exception e) {
            log.debug("Error listing directory {}: {}", path, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<String> getFileContent(String owner, String repo, String path) {
        try {
            apiCallCounter.incrementAndGet();
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (response != null && "file".equals(response.get("type"))) {
                String content = (String) response.get("content");
                String encoding = (String) response.get("encoding");

                if ("base64".equals(encoding) && content != null) {
                    String cleaned = content.replaceAll("\\s", "");
                    return Optional.of(new String(Base64.getDecoder().decode(cleaned)));
                }
            }
            return Optional.empty();
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Error fetching file {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean fileExists(String owner, String repo, String path) {
        try {
            apiCallCounter.incrementAndGet();
            webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> listWorkflowFiles(String owner, String repo) {
        List<Map<String, Object>> contents = listDirectory(owner, repo, ".github/workflows");
        return contents.stream()
                .filter(f -> {
                    String name = (String) f.get("name");
                    return name != null && (name.endsWith(".yml") || name.endsWith(".yaml"));
                })
                .map(f -> ".github/workflows/" + f.get("name"))
                .toList();
    }

    // WORKFLOW RUNS

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getWorkflowRuns(String owner, String repo,
                                                     String workflowFile, int limit) {
        // Extract filename from path
        String filename = workflowFile.contains("/")
                ? workflowFile.substring(workflowFile.lastIndexOf("/") + 1)
                : workflowFile;

        try {
            apiCallCounter.incrementAndGet();
            Map<String, Object> response = webClient.get()
                    .uri(u -> u.path("/repos/{owner}/{repo}/actions/workflows/{workflow}/runs")
                            .queryParam("per_page", Math.min(GitHubConstants.MAX_PAGE_SIZE, limit))
                            .build(owner, repo, filename))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (response != null && response.containsKey("workflow_runs")) {
                List<Map<String, Object>> runs = (List<Map<String, Object>>) response.get("workflow_runs");
                return runs != null ? runs.stream().limit(limit).toList() : Collections.emptyList();
            }
            return Collections.emptyList();
        } catch (WebClientResponseException.NotFound e) {
            log.debug("Workflow not found: {}", workflowFile);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching workflow runs for {}: {}", workflowFile, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public WorkflowRun parseWorkflowRun(Map<String, Object> raw, String workflowFile) {
        // Extract PR number if present
        Integer prNumber = null;
        List<Map<String, Object>> prs = (List<Map<String, Object>>) raw.get("pull_requests");
        if (prs != null && !prs.isEmpty()) {
            Number num = (Number) prs.getFirst().get("number");
            if (num != null) prNumber = num.intValue();
        }

        return WorkflowRun.builder()
                .id(((Number) raw.get("id")).longValue())
                .workflowFile(workflowFile)
                .workflowName((String) raw.get("name"))
                .headSha((String) raw.get("head_sha"))
                .conclusion((String) raw.get("conclusion"))
                .status((String) raw.get("status"))
                .event((String) raw.get("event"))
                .prNumber(prNumber)
                .createdAt(parseInstant((String) raw.get("created_at")))
                .htmlUrl((String) raw.get("html_url"))
                .build();
    }

    // CHECK RUNS

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCheckRuns(String owner, String repo, String sha) {
        List<Map<String, Object>> allRuns = new ArrayList<>();
        int page = 1;

        while (true) {
            try {
                apiCallCounter.incrementAndGet();
                int finalPage = page;
                Map<String, Object> response = webClient.get()
                        .uri(u -> u.path("/repos/{owner}/{repo}/commits/{sha}/check-runs")
                                .queryParam("per_page", GitHubConstants.MAX_PAGE_SIZE)
                                .queryParam("page", finalPage)
                                .build(owner, repo, sha))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(GitHubConstants.TIMEOUT)
                        .block();

                if (response == null) break;

                List<Map<String, Object>> runs = (List<Map<String, Object>>) response.get("check_runs");
                if (runs == null || runs.isEmpty()) break;

                allRuns.addAll(runs);

                Number total = (Number) response.get("total_count");
                if (total == null || allRuns.size() >= total.intValue()) break;
                page++;
            } catch (Exception e) {
                log.debug("Error fetching check runs for SHA {}: {}", sha, e.getMessage());
                break;
            }
        }

        return allRuns;
    }

    // COMMIT STATUSES

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCommitStatuses(String owner, String repo, String sha) {
        try {
            apiCallCounter.incrementAndGet();
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/commits/{sha}/status", owner, repo, sha)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (response == null) {
                return Collections.emptyList();
            }

            // The combined status API returns "statuses" array with latest per context
            List<Map<String, Object>> statuses = (List<Map<String, Object>>) response.get("statuses");
            return statuses != null ? statuses : Collections.emptyList();

        } catch (Exception e) {
            log.debug("Error fetching combined status for SHA {}: {}", sha, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRecentClosedPRs(String owner, String repo, int limit) {
        try {
            apiCallCounter.incrementAndGet();
            List response = webClient.get()
                    .uri(u -> u.path("/repos/{owner}/{repo}/pulls")
                            .queryParam("state", "closed")
                            .queryParam("sort", "updated")
                            .queryParam("direction", "desc")
                            .queryParam("per_page", Math.min(GitHubConstants.MAX_PAGE_SIZE, limit))
                            .build(owner, repo))
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (response == null) return Collections.emptyList();
            List<Map<String, Object>> typedResponse = (List<Map<String, Object>>) response;
            return typedResponse.stream().limit(limit).toList();
        } catch (Exception e) {
            log.error("Error fetching closed PRs: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // PULL REQUESTS

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getPullRequest(String owner, String repo, int prNumber) {
        try {
            apiCallCounter.incrementAndGet();
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{pr}", owner, repo, prNumber)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();
            return Optional.ofNullable(response);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching PR #{}: {}", prNumber, e.getMessage());
            return Optional.empty();
        }
    }

    // RATE LIMIT

    @Override
    @SuppressWarnings("unchecked")
    public int getRemainingRateLimit() {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/rate_limit")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (response != null) {
                Map<String, Object> resources = (Map<String, Object>) response.get("resources");
                Map<String, Object> core = (Map<String, Object>) resources.get("core");
                return ((Number) core.get("remaining")).intValue();
            }
        } catch (Exception e) {
            log.error("Error checking rate limit: {}", e.getMessage());
        }
        return -1;
    }

    // HISTORY ANALYSIS

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCommitsForPath(String owner, String repo, String path, int limit) {
        try {
            apiCallCounter.incrementAndGet();
            List<Map<String, Object>> result = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/commits")
                            .queryParam("path", path)
                            .queryParam("per_page", Math.min(limit, GitHubConstants.MAX_PAGE_SIZE))
                            .build(owner, repo))
                    .retrieve()
                    .bodyToMono(List.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            return result != null ? result : List.of();
        } catch (WebClientResponseException.NotFound e) {
            return List.of();
        } catch (Exception e) {
            log.debug("Failed to get commits for path {}: {}", path, e.getMessage());
            return List.of();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getFirstCommit(String owner, String repo) {
        try {
            apiCallCounter.incrementAndGet();

            List<Map<String, Object>> commits = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/commits")
                            .queryParam("per_page", 1)
                            .build(owner, repo))
                    .retrieve()
                    .bodyToMono(List.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (commits != null && !commits.isEmpty()) {
                return Optional.of(commits.getFirst());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Failed to get first commit: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAllCommits(String owner, String repo) {
        List<Map<String, Object>> allCommits = new ArrayList<>();
        int page = 1;
        try {
            while (true) {
                int finalPage = page;
                apiCallCounter.incrementAndGet();
                List<Map<String, Object>> pageCommits = webClient.get()
                        .uri(u -> u.path("/repos/{owner}/{repo}/commits")
                                .queryParam("per_page", GitHubConstants.MAX_PAGE_SIZE)
                                .queryParam("page", finalPage)
                                .build(owner, repo))
                        .retrieve()
                        .bodyToMono(List.class)
                        .timeout(GitHubConstants.TIMEOUT)
                        .block();

                if (pageCommits == null || pageCommits.isEmpty()) break;
                allCommits.addAll(pageCommits);
                if (pageCommits.size() < GitHubConstants.MAX_PAGE_SIZE) break;
                page++;
            }
            log.info("Fetched {} commits for {}/{} ({} pages)", allCommits.size(), owner, repo, page);
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Repository {}/{} not found on GitHub", owner, repo);
        } catch (Exception e) {
            log.error("Error fetching commit history for {}/{}: {}", owner, repo, e.getMessage());
            throw new RuntimeException("Failed to fetch commits for " + owner + "/" + repo, e);
        }
        return allCommits;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int getCommitCount(String owner, String repo) {
        try {
            apiCallCounter.incrementAndGet();
            List<Map<String, Object>> contributors = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/contributors")
                            .queryParam("per_page", GitHubConstants.MAX_PAGE_SIZE)
                            .queryParam("anon", "true")
                            .build(owner, repo))
                    .retrieve()
                    .bodyToMono(List.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (contributors != null) {
                return contributors.stream()
                        .mapToInt(c -> ((Number) c.getOrDefault("contributions", 0)).intValue())
                        .sum();
            }
            return 0;
        } catch (Exception e) {
            log.debug("Failed to get commit count: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getFileContentAtRef(String owner, String repo, String path, String ref) {
        try {
            apiCallCounter.incrementAndGet();
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/contents/{path}")
                            .queryParam("ref", ref)
                            .build(owner, repo, path))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (response != null && response.containsKey("content")) {
                String content = (String) response.get("content");
                String encoding = (String) response.getOrDefault("encoding", "base64");

                if ("base64".equals(encoding) && content != null) {
                    // Decode base64 content (GitHub sends it with newlines)
                    content = content.replace("\n", "").replace("\r", "");
                    return new String(Base64.getDecoder().decode(content));
                }
                return content;
            }
            return null;
        } catch (WebClientResponseException.NotFound e) {
            return null; // File doesn't exist at this ref
        } catch (Exception e) {
            log.debug("Failed to get file content for {} at {}: {}", path, ref, e.getMessage());
            return null;
        }
    }

    @Override
    public String getFileContentString(String owner, String repo, String path) {
        return getFileContentAtRef(owner, repo, path, "HEAD");
    }

    @Override
    @SuppressWarnings("unchecked")
    public CommitInfo getFileCreationCommit(String owner, String repo, String path) {
        try {
            // Get commits for this file, oldest first is last in the list
            List<Map<String, Object>> commits = getCommitsForPath(owner, repo, path, GitHubConstants.MAX_PAGE_SIZE);

            if (commits.isEmpty()) {
                return null;
            }

            // Last commit is the oldest (file creation)
            Map<String, Object> oldestCommit = commits.getLast();
            return parseCommitToInfo(oldestCommit);

        } catch (Exception e) {
            log.debug("Failed to get file creation commit for {}: {}", path, e.getMessage());
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public CommitInfo blameLineGraphQL(String owner, String repo, String path, int lineNumber) {
        try {
            apiCallCounter.incrementAndGet();

            // GraphQL query for blame - blame is on Commit, not Blob!
            // We use defaultBranchRef to get the default branch automatically
            String query = String.format("""
                    {
                      repository(owner: "%s", name: "%s") {
                        defaultBranchRef {
                          target {
                            ... on Commit {
                              blame(path: "%s") {
                                ranges {
                                  startingLine
                                  endingLine
                                  commit {
                                    oid
                                    message
                                    committedDate
                                    author {
                                      name
                                      email
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                    """, owner, repo, path);

            Map<String, Object> requestBody = Map.of("query", query);

            Map<String, Object> response = webClient.post()
                    .uri(GitHubConstants.GRAPHQL_URL)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (response == null) {
                return null;
            }

            // Navigate: data -> repository -> defaultBranchRef -> target -> blame -> ranges
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) {
                Object errors = response.get("errors");
                if (errors != null) {
                    log.debug("GraphQL error: {}", errors);
                }
                return null;
            }

            Map<String, Object> repository = (Map<String, Object>) data.get("repository");
            if (repository == null) return null;

            Map<String, Object> defaultBranchRef = (Map<String, Object>) repository.get("defaultBranchRef");
            if (defaultBranchRef == null) return null;

            Map<String, Object> target = (Map<String, Object>) defaultBranchRef.get("target");
            if (target == null) return null;

            Map<String, Object> blame = (Map<String, Object>) target.get("blame");
            if (blame == null) return null;

            List<Map<String, Object>> ranges = (List<Map<String, Object>>) blame.get("ranges");
            if (ranges == null || ranges.isEmpty()) return null;

            // Find the range that contains our line
            for (Map<String, Object> range : ranges) {
                int startLine = ((Number) range.get("startingLine")).intValue();
                int endLine = ((Number) range.get("endingLine")).intValue();

                if (lineNumber >= startLine && lineNumber <= endLine) {
                    Map<String, Object> commit = (Map<String, Object>) range.get("commit");
                    if (commit != null) {
                        return parseGraphQLCommit(commit);
                    }
                }
            }

            return null;

        } catch (Exception e) {
            log.debug("GraphQL blame failed for {}:{}: {}", path, lineNumber, e.getMessage());
            return null;
        }
    }

    /**
     * Parse a GraphQL commit response into CommitInfo.
     */
    @SuppressWarnings("unchecked")
    private CommitInfo parseGraphQLCommit(Map<String, Object> commit) {
        String sha = (String) commit.get("oid");
        String message = (String) commit.get("message");
        String dateStr = (String) commit.get("committedDate");

        Map<String, Object> author = (Map<String, Object>) commit.get("author");
        String authorName = author != null ? (String) author.get("name") : "Unknown";

        Instant date = parseInstant(dateStr);

        return CommitInfo.builder()
                .sha(sha)
                .shortSha(sha != null ? sha.substring(0, Math.min(7, sha.length())) : "")
                .author(authorName)
                .date(date)
                .message(message != null ? message.split("\n")[0] : "")
                .build();
    }

    /**
     * Parse a REST API commit response into CommitInfo.
     */
    @SuppressWarnings("unchecked")
    private CommitInfo parseCommitToInfo(Map<String, Object> commitData) {
        String sha = (String) commitData.get("sha");

        Map<String, Object> commit = (Map<String, Object>) commitData.get("commit");
        if (commit == null) return null;

        Map<String, Object> author = (Map<String, Object>) commit.get("author");
        String authorName = author != null ? (String) author.get("name") : "Unknown";
        String dateStr = author != null ? (String) author.get("date") : null;
        String message = (String) commit.get("message");

        Instant date = parseInstant(dateStr);

        return CommitInfo.builder()
                .sha(sha)
                .shortSha(sha != null ? sha.substring(0, Math.min(7, sha.length())) : "")
                .author(authorName)
                .date(date)
                .message(message != null ? message.split("\n")[0] : "")
                .build();
    }

    // PR-BASED INTRODUCTION DETECTION

    @Override
    @SuppressWarnings("unchecked")
    public int getLatestPRNumber(String owner, String repo) {
        try {
            apiCallCounter.incrementAndGet();
            List<Map> prs = webClient.get()
                    .uri(u -> u.path("/repos/{owner}/{repo}/pulls")
                            .queryParam("state", "all")
                            .queryParam("sort", "created")
                            .queryParam("direction", "desc")
                            .queryParam("per_page", 1)
                            .build(owner, repo))
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (prs != null && !prs.isEmpty()) {
                return ((Number) prs.getFirst().get("number")).intValue();
            }
            return 0;
        } catch (Exception e) {
            log.error("Error getting latest PR number: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public int getFirstPRNumber(String owner, String repo) {
        try {
            apiCallCounter.incrementAndGet();
            List<Map> prs = webClient.get()
                    .uri(u -> u.path("/repos/{owner}/{repo}/pulls")
                            .queryParam("state", "all")
                            .queryParam("sort", "created")
                            .queryParam("direction", "asc")
                            .queryParam("per_page", 1)
                            .build(owner, repo))
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (prs != null && !prs.isEmpty()) {
                return ((Number) prs.getFirst().get("number")).intValue();
            }
            return 1;
        } catch (Exception e) {
            log.error("Error getting first PR number: {}", e.getMessage());
            return 1;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPRIfExists(String owner, String repo, int prNumber) {
        try {
            apiCallCounter.incrementAndGet();
            return webClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{pr}", owner, repo, prNumber)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();
        } catch (WebClientResponseException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.debug("Error checking PR #{}: {}", prNumber, e.getMessage());
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public int getTotalPRCount(String owner, String repo) {
        try {
            String query = String.format("repo:%s/%s is:pr", owner, repo);

            apiCallCounter.incrementAndGet();
            Map<String, Object> response = webClient.get()
                    .uri(u -> u.path("/search/issues")
                            .queryParam("q", query)
                            .queryParam("per_page", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(GitHubConstants.TIMEOUT)
                    .block();

            if (response != null && response.containsKey("total_count")) {
                return ((Number) response.get("total_count")).intValue();
            }
            return 0;
        } catch (Exception e) {
            log.error("Error getting PR count: {}", e.getMessage());
            return 0;
        }
    }

    // UTILITIES

    private Instant parseInstant(String s) {
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
