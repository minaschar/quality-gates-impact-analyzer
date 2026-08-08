package com.thesis.qualitygateanalyzer.service.git;

import com.thesis.qualitygateanalyzer.constant.QualityGateDetectionConstants;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CheckRun;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CommitInfo;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CommitStatus;
import com.thesis.qualitygateanalyzer.domain.qualitygate.QGFileIntroduction;
import com.thesis.qualitygateanalyzer.service.github.CheckRunMatcher;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Default {@link PRBasedIntroductionFinder} implementation.
 * <p>
 * Uses binary search on PR numbers to find the first PR where a tool appeared in
 * check runs or commit statuses, requiring approximately log₂(N) API calls for N PRs.
 * <p>
 * Note: this finds when the tool first appeared in PR checks, not when its config was added.
 * For external GitHub Apps (SonarCloud, Codecov) these are effectively the same.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PRBasedIntroductionFinderImpl implements PRBasedIntroductionFinder {

    private final GitHubApiClient github;
    private final CheckRunMatcher checkRunMatcher;

    // Cache to avoid re-checking the same PRs within a single search
    private final Map<Integer, PRCheckResult> prCache = new HashMap<>();

    @Override
    public QGFileIntroduction findToolIntroduction(String owner, String repo, QualityGateTool tool) {
        log.info("Searching for the first PR with {} using binary search", tool.getDisplayName());

        // Clear cache for new search
        prCache.clear();

        try {
            // Get PR range
            int firstPR = github.getFirstPRNumber(owner, repo);
            int latestPR = github.getLatestPRNumber(owner, repo);

            if (latestPR == 0) {
                log.info("No pull requests found in repository");
                return null;
            }

            log.debug("PR range: #{} to #{} ({} total)", firstPR, latestPR, latestPR - firstPR + 1);

            // Binary search for the first PR with the tool
            PRCheckResult firstWithTool = binarySearchFirstPRWithTool(owner, repo, firstPR, latestPR, tool);

            if (firstWithTool == null) {
                log.info("{} not found in any pull request check runs", tool.getDisplayName());
                return null;
            }

            log.info("{} first appeared in PR #{} on {} by {}",
                    tool.getDisplayName(),
                    firstWithTool.prNumber,
                    firstWithTool.prCreatedAt,
                    firstWithTool.prAuthor);

            // Build the introduction result
            CommitInfo introCommit = CommitInfo.builder()
                    .sha(firstWithTool.headSha)
                    .shortSha(firstWithTool.headSha.substring(0, Math.min(7, firstWithTool.headSha.length())))
                    .author(firstWithTool.prAuthor)
                    .date(firstWithTool.prCreatedAt)
                    .message(String.format("PR #%d: %s", firstWithTool.prNumber, firstWithTool.prTitle))
                    .build();

            return QGFileIntroduction.builder()
                    .filePath(String.format("PR #%d (external app)", firstWithTool.prNumber))
                    .searchPattern(String.format("%s check run", tool.getDisplayName()))
                    .introducedAt(introCommit)
                    .presentSinceFileCreation(false)
                    .allOccurrences(List.of(introCommit))
                    .build();

        } catch (Exception e) {
            log.error("PR-based search for {} failed: {}", tool.getDisplayName(), e.getMessage());
            return null;
        }
    }

    /**
     * Binary search to find the first (oldest) PR where the tool appears in check runs.
     * <p>
     * If the tool is found at the midpoint, searches the lower half for an earlier occurrence.
     * If not found, searches the upper half. Switches to linear search for small ranges.
     */
    private PRCheckResult binarySearchFirstPRWithTool(
            String owner, String repo, int low, int high, QualityGateTool tool) {

        PRCheckResult earliestFound = null;
        int iterations = 0;

        while (low <= high && iterations < QualityGateDetectionConstants.MAX_BINARY_SEARCH_ITERATIONS) {
            iterations++;

            int mid = low + (high - low) / 2;

            // If the range is small enough, switch to linear search
            if (high - low < QualityGateDetectionConstants.LINEAR_SEARCH_THRESHOLD) {
                return linearSearchFirstPRWithTool(owner, repo, low, high, tool, earliestFound);
            }

            // Check the midpoint PR
            PRCheckResult midResult = checkPRForTool(owner, repo, mid, tool);

            if (midResult != null && midResult.hasToolCheck) {
                earliestFound = midResult;
                high = mid - 1;
                log.debug("Found {} at PR #{}, searching for earlier occurrence", tool.getDisplayName(), mid);
            } else {
                low = mid + 1;
                log.debug("{} not found at PR #{}, searching later range", tool.getDisplayName(), mid);
            }
        }

        if (iterations >= QualityGateDetectionConstants.MAX_BINARY_SEARCH_ITERATIONS) {
            log.warn("Binary search exceeded maximum iteration limit");
        }

        return earliestFound;
    }

    /**
     * Linear search for small ranges - more thorough than binary search.
     * Finds the actual first PR with the tool.
     */
    private PRCheckResult linearSearchFirstPRWithTool(
            String owner, String repo, int low, int high,
            QualityGateTool tool, PRCheckResult bestSoFar) {

        log.debug("Linear search from PR #{} to #{}", low, high);

        PRCheckResult earliest = bestSoFar;

        for (int pr = low; pr <= high; pr++) {
            PRCheckResult result = checkPRForTool(owner, repo, pr, tool);

            if (result != null && result.hasToolCheck) {
                if (earliest == null || result.prNumber < earliest.prNumber) {
                    earliest = result;
                }
                // Stop at the first match since we search from oldest to newest
                break;
            }
        }

        return earliest;
    }

    /**
     * Check if a specific PR has check runs/statuses for the given tool.
     * Uses caching to avoid redundant API calls.
     */
    private PRCheckResult checkPRForTool(String owner, String repo, int prNumber, QualityGateTool tool) {
        // Check cache first
        if (prCache.containsKey(prNumber)) {
            PRCheckResult cached = prCache.get(prNumber);
            // Re-evaluate for this specific tool
            if (cached == null) return null;
            return evaluatePRForTool(cached, tool);
        }

        try {
            // Get PR details
            Map<String, Object> pr = github.getPRIfExists(owner, repo, prNumber);

            if (pr == null) {
                // PR doesn't exist (deleted or never existed)
                prCache.put(prNumber, null);
                return null;
            }

            // Extract PR info
            String headSha = extractHeadSha(pr);
            String prTitle = (String) pr.get("title");
            String prAuthor = extractAuthor(pr);
            Instant prCreatedAt = parseInstant((String) pr.get("created_at"));

            if (headSha == null) {
                prCache.put(prNumber, null);
                return null;
            }

            // Get check runs for this PR's head SHA
            List<Map<String, Object>> rawCheckRuns = github.getCheckRuns(owner, repo, headSha);
            List<CheckRun> checkRuns = rawCheckRuns.stream()
                    .map(checkRunMatcher::parseAndMatch)
                    .toList();

            // Get commit statuses
            List<Map<String, Object>> rawStatuses = github.getCommitStatuses(owner, repo, headSha);
            List<CommitStatus> statuses = rawStatuses.stream()
                    .map(checkRunMatcher::parseAndMatchStatus)
                    .toList();

            // Build result with all tools found
            Set<QualityGateTool> toolsFound = new HashSet<>();
            for (CheckRun cr : checkRuns) {
                if (cr.getMatchedTool() != null) {
                    toolsFound.add(cr.getMatchedTool());
                }
            }
            for (CommitStatus cs : statuses) {
                if (cs.getMatchedTool() != null) {
                    toolsFound.add(cs.getMatchedTool());
                }
            }

            PRCheckResult result = new PRCheckResult(
                    prNumber,
                    headSha,
                    prTitle,
                    prAuthor,
                    prCreatedAt,
                    toolsFound,
                    toolsFound.contains(tool)
            );

            prCache.put(prNumber, result);
            return result;

        } catch (Exception e) {
            log.debug("Error checking PR #{}: {}", prNumber, e.getMessage());
            prCache.put(prNumber, null);
            return null;
        }
    }

    /**
     * Re-evaluate a cached PR result for a specific tool.
     */
    private PRCheckResult evaluatePRForTool(PRCheckResult cached, QualityGateTool tool) {
        return new PRCheckResult(
                cached.prNumber,
                cached.headSha,
                cached.prTitle,
                cached.prAuthor,
                cached.prCreatedAt,
                cached.toolsFound,
                cached.toolsFound.contains(tool)
        );
    }

    /**
     * Extract head SHA from PR response.
     */
    @SuppressWarnings("unchecked")
    private String extractHeadSha(Map<String, Object> pr) {
        Map<String, Object> head = (Map<String, Object>) pr.get("head");
        return head != null ? (String) head.get("sha") : null;
    }

    /**
     * Extract author from PR response.
     */
    @SuppressWarnings("unchecked")
    private String extractAuthor(Map<String, Object> pr) {
        Map<String, Object> user = (Map<String, Object>) pr.get("user");
        return user != null ? (String) user.get("login") : "Unknown";
    }

    /**
     * Parse ISO instant string.
     */
    private Instant parseInstant(String s) {
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Result of checking a PR for QG tools.
     */
    private record PRCheckResult(
            int prNumber,
            String headSha,
            String prTitle,
            String prAuthor,
            Instant prCreatedAt,
            Set<QualityGateTool> toolsFound,
            boolean hasToolCheck
    ) {
    }
}
