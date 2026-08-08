package com.thesis.qualitygateanalyzer.service.github;

import com.thesis.qualitygateanalyzer.domain.qualitygate.BranchProtection;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CommitInfo;
import com.thesis.qualitygateanalyzer.domain.qualitygate.WorkflowRun;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GitHub REST and GraphQL API client for quality gate analysis.
 * Provides all endpoints required for repository inspection, workflow run retrieval,
 * check run and commit status fetching, and git history analysis via blame.
 */
public interface GitHubApiClient {

    /**
     * Reset API call counter.
     */
    void resetCallCounter();

    /**
     * Get number of API calls made.
     */
    int getApiCallCount();

    /**
     * Get repository metadata.
     */
    Optional<Map<String, Object>> getRepository(String owner, String repo);

    /**
     * Get branch protection settings for a branch.
     * Returns empty Optional if branch is not protected or API fails.
     */
    Optional<BranchProtection> getBranchProtection(String owner, String repo, String branch);

    /**
     * List files in a directory.
     */
    List<Map<String, Object>> listDirectory(String owner, String repo, String path);

    /**
     * Get file content (decoded from base64).
     */
    Optional<String> getFileContent(String owner, String repo, String path);

    /**
     * Check if file exists.
     */
    boolean fileExists(String owner, String repo, String path);

    /**
     * List workflow files in .github/workflows.
     */
    List<String> listWorkflowFiles(String owner, String repo);

    /**
     * Get workflow runs for a specific workflow file.
     */
    List<Map<String, Object>> getWorkflowRuns(String owner, String repo, String workflowFile, int limit);

    /**
     * Parse a raw workflow run into our model.
     */
    WorkflowRun parseWorkflowRun(Map<String, Object> raw, String workflowFile);

    /**
     * Get check runs for a specific commit SHA.
     */
    List<Map<String, Object>> getCheckRuns(String owner, String repo, String sha);

    /**
     * Get commit statuses for a specific commit SHA.
     * <p>
     * IMPORTANT: Uses the COMBINED STATUS API which returns:
     * - The overall state (success, failure, pending, error)
     * - The LATEST status per context (not all historical statuses)
     * <p>
     * This is crucial because the /statuses endpoint returns ALL statuses
     * sorted by date DESC, so if a check ran multiple times, we might see
     * success from a re-run instead of the original failure.
     */
    List<Map<String, Object>> getCommitStatuses(String owner, String repo, String sha);

    /**
     * Get recent closed PRs for analysis.
     * Used to find PRs with external quality gate failures that may not appear in workflow runs.
     */
    List<Map<String, Object>> getRecentClosedPRs(String owner, String repo, int limit);

    /**
     * Get a specific PR.
     */
    Optional<Map<String, Object>> getPullRequest(String owner, String repo, int prNumber);

    /**
     * Get remaining rate limit.
     */
    int getRemainingRateLimit();

    /**
     * Get commits that modified a specific file path.
     * Returns commits in reverse chronological order (newest first).
     */
    List<Map<String, Object>> getCommitsForPath(String owner, String repo, String path, int limit);

    /**
     * Attempts to retrieve the first commit of a repository.
     * Note: without full pagination this returns an approximation used only for metadata.
     */
    Optional<Map<String, Object>> getFirstCommit(String owner, String repo);

    /**
     * Fetch the complete commit history for a repository's default branch.
     * Paginates through all pages (100 commits per request) and returns them
     * in reverse-chronological order (newest first), matching GitHub's default ordering.
     * The caller is responsible for reversing the list when chronological order is needed.
     */
    List<Map<String, Object>> getAllCommits(String owner, String repo);

    /**
     * Estimate total commit count for a repository using the contributors endpoint.
     */
    int getCommitCount(String owner, String repo);

    /**
     * Get file content at a specific commit ref, decoded from base64.
     * Returns null if the file does not exist at the given ref.
     */
    String getFileContentAtRef(String owner, String repo, String path, String ref);

    /**
     * Convenience method returning file content at HEAD as a String, or null if not found.
     * Unlike {@link #getFileContent}, which returns an Optional.
     */
    String getFileContentString(String owner, String repo, String path);

    /**
     * Get the commit that first introduced a file (oldest commit for the given path).
     */
    CommitInfo getFileCreationCommit(String owner, String repo, String path);

    /**
     * Uses the GitHub GraphQL Blame API to find which commit introduced a specific line.
     */
    CommitInfo blameLineGraphQL(String owner, String repo, String path, int lineNumber);

    /**
     * Get the latest PR number in the repository.
     * Used as the upper bound for binary search.
     */
    int getLatestPRNumber(String owner, String repo);

    /**
     * Get the first (oldest) PR number in the repository.
     * Used as the lower bound for binary search.
     */
    int getFirstPRNumber(String owner, String repo);

    /**
     * Get a PR by number, or null if it does not exist.
     */
    Map<String, Object> getPRIfExists(String owner, String repo, int prNumber);

    /**
     * Get total PR count for a repository using the Search API.
     */
    int getTotalPRCount(String owner, String repo);
}
