package com.thesis.qualitygateanalyzer.service.git;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.*;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Default {@link GitHistoryAnalyzer} implementation, using the GitHub GraphQL Blame API.
 * <p>
 * For each detected quality gate tool, the analyzer locates the relevant configuration
 * file, finds the line containing the tool's keyword, and uses git blame to identify
 * the exact commit that introduced it. If the blame approach fails (e.g. for external
 * GitHub Apps with no configuration file), a PR-based binary search is used as a fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHistoryAnalyzerImpl implements GitHistoryAnalyzer {

    private final GitHubApiClient github;
    private final PRBasedIntroductionFinder prBasedFinder;

    @Override
    public QualityGateHistoryDetection analyzeHistory(
            String owner,
            String repo,
            List<QualityGateDetection> detections) {

        Instant startTime = Instant.now();
        int initialApiCalls = github.getApiCallCount();

        try {
            log.info("Analyzing quality gate history via GraphQL Blame API...");

            CommitInfo repoFirstCommit = getRepoFirstCommit(owner, repo);
            int totalCommits = estimateTotalCommits(owner, repo);

            if (repoFirstCommit != null) {
                log.info("Repository has approximately {} commits", totalCommits);
            }

            // Analyze each detected tool
            List<QGToolIntroduction> toolIntroductions = new ArrayList<>();
            Set<QualityGateTool> analyzedTools = new HashSet<>();
            int filesScanned = 0;

            // Group detections by tool to avoid duplicates
            Map<QualityGateTool, List<QualityGateDetection>> detectionsByTool = new LinkedHashMap<>();
            for (QualityGateDetection detection : detections) {
                detectionsByTool
                        .computeIfAbsent(detection.getTool(), k -> new ArrayList<>())
                        .add(detection);
            }

            for (Map.Entry<QualityGateTool, List<QualityGateDetection>> entry : detectionsByTool.entrySet()) {
                QualityGateTool tool = entry.getKey();
                List<QualityGateDetection> toolDetections = entry.getValue();

                log.debug("Analyzing {} ({} source detections)", tool.getDisplayName(), toolDetections.size());

                QGToolIntroduction introduction = analyzeToolIntroduction(
                        owner, repo, tool, toolDetections, repoFirstCommit);

                if (introduction != null) {
                    toolIntroductions.add(introduction);
                    filesScanned += introduction.getConfigIntroductions().size() +
                            introduction.getCiIntroductions().size();

                    if (introduction.getEffectiveIntroductionCommit() != null) {
                        log.info("{} introduced at commit {} ({}) by {}",
                                tool.getDisplayName(),
                                introduction.getEffectiveIntroductionCommit().getShortSha(),
                                introduction.getEffectiveDate(),
                                introduction.getEffectiveIntroductionCommit().getAuthor());
                    } else {
                        log.warn("{} introduction commit could not be determined", tool.getDisplayName());
                    }
                }

                analyzedTools.add(tool);
            }

            long analysisDuration = System.currentTimeMillis() - startTime.toEpochMilli();
            int apiCallsUsed = github.getApiCallCount() - initialApiCalls;
            log.info("History analysis completed in {}ms using {} API calls", analysisDuration, apiCallsUsed);

            // Sort by effective date (earliest first)
            toolIntroductions.sort((a, b) -> {
                if (a.getEffectiveDate() == null) return 1;
                if (b.getEffectiveDate() == null) return -1;
                return a.getEffectiveDate().compareTo(b.getEffectiveDate());
            });

            // Find earliest and latest
            QGToolIntroduction earliest = toolIntroductions.stream()
                    .filter(t -> t.getEffectiveDate() != null)
                    .findFirst().orElse(null);
            QGToolIntroduction latest = toolIntroductions.stream()
                    .filter(t -> t.getEffectiveDate() != null)
                    .reduce((a, b) -> b).orElse(null);

            return QualityGateHistoryDetection.builder()
                    .toolIntroductions(toolIntroductions)
                    .earliestIntroduction(earliest)
                    .latestIntroduction(latest)
                    .repoFirstCommit(repoFirstCommit)
                    .totalRepoCommits(totalCommits)
                    .metadata(QualityGateHistoryDetection.HistoryDetectionMetadata.builder()
                            .method("graphql-blame+pr-fallback")
                            .cloneType("none")
                            .cloneDirectory(null)
                            .cloneDurationMs(0)
                            .detectionDurationMs(analysisDuration)
                            .totalDurationMs(analysisDuration)
                            .filesScanned(filesScanned)
                            .toolsScanned(analyzedTools.size())
                            .success(true)
                            .errorMessage(null)
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("History analysis failed", e);
            return buildErrorResult(e.getMessage(), startTime);
        }
    }

    /**
     * Analyze when a specific tool was introduced, across all detected source files.
     */
    private QGToolIntroduction analyzeToolIntroduction(
            String owner,
            String repo,
            QualityGateTool tool,
            List<QualityGateDetection> detections,
            CommitInfo repoFirstCommit) {

        List<QGFileIntroduction> configIntros = new ArrayList<>();
        List<QGFileIntroduction> ciIntros = new ArrayList<>();

        // Process each detection (each may be from a different file)
        Set<String> processedFiles = new HashSet<>();

        for (QualityGateDetection detection : detections) {
            String sourceFile = detection.getSourceFile();

            // Skip if we already processed this file for this tool
            if (sourceFile == null || processedFiles.contains(sourceFile)) {
                continue;
            }
            processedFiles.add(sourceFile);

            QGFileIntroduction fileIntro = null;

            // Check if this is a dedicated config file - if so, file creation = tool introduction
            if (isDedicatedConfigFile(sourceFile)) {
                log.debug("{} is a dedicated config file, using file creation commit", sourceFile);
                fileIntro = analyzeFileCreation(owner, repo, sourceFile);
            } else {
                List<String> keywords = extractSearchableKeywords(detection.getEvidenceFound(), tool);

                if (keywords.isEmpty()) {
                    log.debug("No searchable keywords for {} in {}", tool, sourceFile);
                    continue;
                }

                // Analyze this file with blame
                fileIntro = analyzeFileWithBlame(owner, repo, sourceFile, keywords, tool);
            }

            if (fileIntro != null) {
                // Categorize as config or CI
                if (isWorkflowFile(sourceFile)) {
                    ciIntros.add(fileIntro);
                } else {
                    configIntros.add(fileIntro);
                }
            }
        }

        // Also check dedicated config files for this tool
        for (String configFile : tool.getConfigFileNames()) {
            if (processedFiles.contains(configFile)) continue;

            // For dedicated config files, the file creation IS the introduction
            QGFileIntroduction fileIntro = analyzeFileCreation(owner, repo, configFile);
            if (fileIntro != null) {
                configIntros.add(fileIntro);
                processedFiles.add(configFile);
            }
        }

        // Fallback: if no introduction found via blame, use PR-based detection.
        // Handles external GitHub Apps (SonarCloud, Codecov) that have no config file in the repository.
        if (configIntros.isEmpty() && ciIntros.isEmpty()) {
            log.debug("No blame-based introduction found for {}, falling back to PR-based search",
                    tool.getDisplayName());

            QGFileIntroduction prBasedIntro = prBasedFinder.findToolIntroduction(owner, repo, tool);
            if (prBasedIntro != null) {
                ciIntros.add(prBasedIntro);
                log.info("{} introduction identified via PR-based search", tool.getDisplayName());
            }
        }

        // Find effective introduction (earliest across all files)
        CommitInfo effectiveCommit = null;
        Instant effectiveDate = null;
        boolean presentSinceBeginning = false;

        List<QGFileIntroduction> allIntros = new ArrayList<>();
        allIntros.addAll(configIntros);
        allIntros.addAll(ciIntros);

        for (QGFileIntroduction intro : allIntros) {
            if (intro.getIntroducedAt() != null) {
                Instant introDate = intro.getIntroducedAt().getDate();
                if (introDate != null && (effectiveDate == null || introDate.isBefore(effectiveDate))) {
                    effectiveDate = introDate;
                    effectiveCommit = intro.getIntroducedAt();
                }

                // Check if this was the repo's first commit
                if (repoFirstCommit != null &&
                        intro.getIntroducedAt().getSha().equals(repoFirstCommit.getSha())) {
                    presentSinceBeginning = true;
                }
            }
        }

        // Build summary
        String summary;
        if (presentSinceBeginning) {
            summary = String.format("%s has been present since repository creation",
                    tool.getDisplayName());
        } else if (effectiveCommit != null) {
            summary = String.format("%s was introduced in commit %s on %s by %s",
                    tool.getDisplayName(),
                    effectiveCommit.getShortSha(),
                    effectiveDate,
                    effectiveCommit.getAuthor());
        } else {
            summary = String.format("%s introduction commit could not be determined",
                    tool.getDisplayName());
        }

        return QGToolIntroduction.builder()
                .tool(tool)
                .category(tool.getCategory())
                .configIntroductions(configIntros)
                .ciIntroductions(ciIntros)
                .effectiveIntroductionCommit(effectiveCommit)
                .effectiveDate(effectiveDate)
                .presentSinceRepoCreation(presentSinceBeginning)
                .introductionSummary(summary)
                .build();
    }

    /**
     * Find the exact commit that introduced a quality gate keyword in the given file,
     * using the GraphQL Blame API.
     */
    private QGFileIntroduction analyzeFileWithBlame(
            String owner,
            String repo,
            String filePath,
            List<String> keywords,
            QualityGateTool tool) {

        try {
            // Step 1: Get current file content
            String content = github.getFileContentString(owner, repo, filePath);
            if (content == null) {
                log.debug("File not found: {}", filePath);
                return null;
            }

            // Find which line contains the keyword, then blame that exact line.
            // Line numbers in the Blame API are 1-based.
            String[] lines = content.split("\n");
            Integer targetLine = null;
            String matchedKeyword = null;

            for (String keyword : keywords) {
                String searchKeyword = cleanKeyword(keyword);
                if (searchKeyword.isEmpty()) continue;

                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].toLowerCase().contains(searchKeyword.toLowerCase())) {
                        targetLine = i + 1;
                        matchedKeyword = searchKeyword;
                        break;
                    }
                }
                if (targetLine != null) break;
            }

            if (targetLine == null) {
                log.debug("Keywords not found in {}: {}", filePath, keywords);
                return null;
            }

            log.debug("Found '{}' at line {} in {}", matchedKeyword, targetLine, filePath);

            CommitInfo blameCommit = github.blameLineGraphQL(owner, repo, filePath, targetLine);

            if (blameCommit == null) {
                log.debug("Blame returned no result for {}:{}", filePath, targetLine);
                return null;
            }

            log.debug("Line {} in {} introduced by {} (commit {})",
                    targetLine, filePath, blameCommit.getAuthor(), blameCommit.getShortSha());

            return QGFileIntroduction.builder()
                    .filePath(filePath)
                    .searchPattern(matchedKeyword)
                    .introducedAt(blameCommit)
                    .presentSinceFileCreation(false)
                    .allOccurrences(List.of(blameCommit))
                    .build();

        } catch (Exception e) {
            log.debug("Error analyzing {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    /**
     * Find when a dedicated config file was created.
     * For files like .codecov.yml, the file creation date equals the tool introduction date.
     */
    private QGFileIntroduction analyzeFileCreation(String owner, String repo, String filePath) {
        try {
            // Check if file exists
            if (!github.fileExists(owner, repo, filePath)) {
                return null;
            }

            // Get the first commit that introduced this file
            CommitInfo creationCommit = github.getFileCreationCommit(owner, repo, filePath);

            if (creationCommit == null) {
                return null;
            }

            log.debug("Config file {} created in commit {}", filePath, creationCommit.getShortSha());

            return QGFileIntroduction.builder()
                    .filePath(filePath)
                    .searchPattern("file creation")
                    .introducedAt(creationCommit)
                    .presentSinceFileCreation(true)
                    .allOccurrences(List.of(creationCommit))
                    .build();

        } catch (Exception e) {
            log.debug("Error checking file creation for {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    /**
     * Extract searchable keywords from evidence.
     * Filters out generic patterns and extracts concrete search terms.
     */
    private List<String> extractSearchableKeywords(List<String> evidence, QualityGateTool tool) {
        List<String> keywords = new ArrayList<>();

        if (evidence != null) {
            for (String e : evidence) {
                String cleaned = cleanKeyword(e);
                if (!cleaned.isEmpty() && isSearchableKeyword(cleaned)) {
                    keywords.add(cleaned);
                }
            }
        }

        // Add tool-specific patterns as fallback
        if (keywords.isEmpty()) {
            keywords.addAll(tool.getBuildToolPatterns());
            keywords.addAll(tool.getWorkflowActionPatterns());
        }

        return keywords;
    }

    /**
     * Clean a keyword for searching.
     * Removes annotations like "(enforcing)", "keyword:", etc.
     */
    private String cleanKeyword(String keyword) {
        if (keyword == null) return "";

        return keyword
                .replaceAll("\\(enforcing\\)", "")
                .replaceAll("\\(.*?\\)", "")  // Remove anything in parentheses
                .replaceAll("keyword:\\s*", "")
                .replace("failsOnViolation=true", "")
                .replace("failOnError=true", "")
                .replace("triggers on PR", "")
                .trim();
    }

    /**
     * Check if a keyword is searchable (not too generic).
     */
    private boolean isSearchableKeyword(String keyword) {
        if (keyword == null || keyword.length() < 3) return false;

        // Skip very generic keywords
        Set<String> genericKeywords = Set.of(
                "coverage", "test", "build", "run", "check", "lint",
                "codecov config", "triggers on pr"
        );

        return !genericKeywords.contains(keyword.toLowerCase());
    }

    /**
     * Check if a file is a workflow/CI file.
     */
    private boolean isWorkflowFile(String filePath) {
        return filePath.contains(".github/workflows") ||
                filePath.contains(".circleci") ||
                filePath.contains("Jenkinsfile") ||
                filePath.contains(".travis.yml") ||
                filePath.contains("azure-pipelines");
    }

    /**
     * Check if a file is a dedicated config file for a QG tool.
     * For these files, the file creation IS the tool introduction.
     */
    private boolean isDedicatedConfigFile(String filePath) {
        if (filePath == null) return false;

        String fileName = filePath.contains("/")
                ? filePath.substring(filePath.lastIndexOf("/") + 1)
                : filePath;

        // Dedicated config files - file creation = tool introduction
        Set<String> dedicatedFiles = Set.of(
                ".codecov.yml", ".codecov.yaml", "codecov.yml", "codecov.yaml",
                ".coveragerc", ".coverage",
                "sonar-project.properties",
                ".codacy.yml", ".codacy.yaml",
                "checkstyle.xml", "checkstyle-config.xml",
                ".eslintrc", ".eslintrc.js", ".eslintrc.json", ".eslintrc.yml",
                ".prettierrc", ".prettierrc.js", ".prettierrc.json", ".prettierrc.yml",
                ".pylintrc", "pylintrc",
                ".flake8",
                "mypy.ini", ".mypy.ini",
                "ruff.toml",
                ".golangci.yml", ".golangci.yaml",
                "detekt.yml", "detekt.yaml",
                "spotbugs-exclude.xml", "spotbugs-include.xml"
        );

        return dedicatedFiles.contains(fileName);
    }

    /**
     * Get the repository's first commit.
     */
    private CommitInfo getRepoFirstCommit(String owner, String repo) {
        try {
            return github.getFirstCommit(owner, repo)
                    .map(this::parseCommit)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Failed to get first commit: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Estimate total commits (for metadata).
     */
    private int estimateTotalCommits(String owner, String repo) {
        try {
            return github.getCommitCount(owner, repo);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Parse a GitHub API commit response into CommitInfo.
     */
    private CommitInfo parseCommit(Map<String, Object> commitData) {
        String sha = (String) commitData.get("sha");

        @SuppressWarnings("unchecked")
        Map<String, Object> commit = (Map<String, Object>) commitData.get("commit");

        @SuppressWarnings("unchecked")
        Map<String, Object> author = (Map<String, Object>) commit.get("author");

        String authorName = author != null ? (String) author.get("name") : "Unknown";
        String dateStr = author != null ? (String) author.get("date") : null;
        String message = (String) commit.get("message");

        Instant date = null;
        if (dateStr != null) {
            try {
                date = Instant.parse(dateStr);
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        return CommitInfo.builder()
                .sha(sha)
                .shortSha(sha.substring(0, Math.min(7, sha.length())))
                .author(authorName)
                .date(date)
                .message(message != null ? message.split("\n")[0] : "")
                .build();
    }

    /**
     * Build error result when analysis fails.
     */
    private QualityGateHistoryDetection buildErrorResult(String errorMessage, Instant startTime) {
        return QualityGateHistoryDetection.builder()
                .toolIntroductions(List.of())
                .earliestIntroduction(null)
                .latestIntroduction(null)
                .repoFirstCommit(null)
                .totalRepoCommits(0)
                .metadata(QualityGateHistoryDetection.HistoryDetectionMetadata.builder()
                        .method("graphql-blame")
                        .cloneType("none")
                        .cloneDirectory(null)
                        .cloneDurationMs(0)
                        .detectionDurationMs(System.currentTimeMillis() - startTime.toEpochMilli())
                        .totalDurationMs(System.currentTimeMillis() - startTime.toEpochMilli())
                        .filesScanned(0)
                        .toolsScanned(0)
                        .success(false)
                        .errorMessage(errorMessage)
                        .build())
                .build();
    }
}
