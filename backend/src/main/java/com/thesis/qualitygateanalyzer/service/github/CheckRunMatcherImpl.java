package com.thesis.qualitygateanalyzer.service.github;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CheckRun;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CommitStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Default {@link CheckRunMatcher} implementation.
 * <p>
 * Handles three matching scenarios:
 * 1. Direct quality gate app contexts (SonarCloud, Codecov, Codacy, etc.)
 * 2. CI/CD providers wrapping quality gate tools (CodeBuild, CircleCI, Jenkins, etc.)
 * 3. Generic patterns via the QualityGateTool enum
 */
@Slf4j
@Component
public class CheckRunMatcherImpl implements CheckRunMatcher {

    // CI/CD provider patterns - these wrap QG tools in their own status contexts
    private static final Set<String> CICD_PROVIDERS = Set.of(
            "codebuild", "circleci", "jenkins", "travis", "github-actions",
            "azure", "gitlab", "bitbucket", "buildkite", "teamcity", "appveyor",
            "drone", "concourse", "bamboo", "wercker", "semaphore", "buddy"
    );

    // Direct QG tool keywords for quick matching (used when inside CI/CD contexts)
    private static final Map<String, QualityGateTool> QUICK_MATCH_KEYWORDS = createQuickMatchMap();

    private static Map<String, QualityGateTool> createQuickMatchMap() {
        Map<String, QualityGateTool> map = new LinkedHashMap<>();

        // Code Quality tools (highest priority - thesis focus)
        map.put("sonarcloud", QualityGateTool.SONARCLOUD);
        map.put("sonarqube", QualityGateTool.SONARQUBE);
        map.put("sonar-cloud", QualityGateTool.SONARCLOUD);
        map.put("sonar", QualityGateTool.SONARQUBE);  // Generic "sonar" defaults to SonarQube
        map.put("codacy", QualityGateTool.CODACY);
        map.put("codeclimate", QualityGateTool.CODE_CLIMATE);
        map.put("code-climate", QualityGateTool.CODE_CLIMATE);
        map.put("qodana", QualityGateTool.QODANA);
        map.put("deepsource", QualityGateTool.DEEPSOURCE);

        // Coverage tools
        map.put("codecov", QualityGateTool.CODECOV);
        map.put("coveralls", QualityGateTool.COVERALLS);
        map.put("jacoco", QualityGateTool.JACOCO);
        map.put("cobertura", QualityGateTool.COBERTURA);
        map.put("istanbul", QualityGateTool.ISTANBUL);
        map.put("nyc", QualityGateTool.ISTANBUL);

        // Java linters
        map.put("checkstyle", QualityGateTool.CHECKSTYLE);
        map.put("spotbugs", QualityGateTool.SPOTBUGS);
        map.put("findbugs", QualityGateTool.SPOTBUGS);
        map.put("pmd", QualityGateTool.PMD);

        // JavaScript/TypeScript linters
        map.put("eslint", QualityGateTool.ESLINT);
        map.put("prettier", QualityGateTool.PRETTIER);

        // Python linters
        map.put("pylint", QualityGateTool.PYLINT);
        map.put("flake8", QualityGateTool.FLAKE8);
        map.put("mypy", QualityGateTool.MYPY);
        map.put("black", QualityGateTool.BLACK);
        map.put("ruff", QualityGateTool.RUFF);

        // Go linters
        map.put("golangci", QualityGateTool.GOLANGCI_LINT);
        map.put("golint", QualityGateTool.GOLANGCI_LINT);

        // Kotlin linters
        map.put("ktlint", QualityGateTool.KTLINT);
        map.put("detekt", QualityGateTool.DETEKT);

        // Ruby linters
        map.put("rubocop", QualityGateTool.RUBOCOP);

        // PHP linters
        map.put("phpstan", QualityGateTool.PHPSTAN);
        map.put("psalm", QualityGateTool.PSALM);
        map.put("phpcs", QualityGateTool.PHPCS);

        // Rust linters
        map.put("clippy", QualityGateTool.CLIPPY);
        map.put("rustfmt", QualityGateTool.RUSTFMT);

        // Swift linters
        map.put("swiftlint", QualityGateTool.SWIFTLINT);

        // Security tools
        map.put("snyk", QualityGateTool.SNYK);
        map.put("trivy", QualityGateTool.TRIVY);
        map.put("semgrep", QualityGateTool.SEMGREP);
        map.put("dependabot", QualityGateTool.DEPENDABOT);

        return Collections.unmodifiableMap(map);
    }

    // CHECK RUN PARSING (GitHub Actions results)

    @Override
    @SuppressWarnings("unchecked")
    public CheckRun parseAndMatch(Map<String, Object> raw) {
        String name = (String) raw.get("name");
        String status = (String) raw.get("status");
        String conclusion = (String) raw.get("conclusion");
        String headSha = (String) raw.get("head_sha");

        // Extract app slug
        String appSlug = null;
        Map<String, Object> app = (Map<String, Object>) raw.get("app");
        if (app != null) {
            appSlug = (String) app.get("slug");
        }

        // Extract output
        String outputTitle = null;
        String outputSummary = null;
        Map<String, Object> output = (Map<String, Object>) raw.get("output");
        if (output != null) {
            outputTitle = (String) output.get("title");
            outputSummary = truncate((String) output.get("summary"), 500);
        }

        // Match to QG tool
        QualityGateTool matchedTool = matchCheckRun(name, appSlug);
        double confidence = matchedTool != null ? calculateCheckRunConfidence(name, appSlug, matchedTool) : 0.0;

        if (matchedTool == null && "failure".equalsIgnoreCase(conclusion)) {
            String lowerName = name != null ? name.toLowerCase() : "";
            if (lowerName.contains("sonar") || lowerName.contains("quality gate")) {
                log.debug("Unmatched failed check run that may be quality gate related: name='{}', appSlug='{}'",
                        name, appSlug);
            }
        }

        return CheckRun.builder()
                .id(((Number) raw.get("id")).longValue())
                .name(name)
                .appSlug(appSlug)
                .status(status)
                .conclusion(conclusion)
                .outputTitle(outputTitle)
                .outputSummary(outputSummary)
                .headSha(headSha)
                .completedAt(parseInstant((String) raw.get("completed_at")))
                .matchedTool(matchedTool)
                .matchConfidence(confidence)
                .build();
    }

    /**
     * Match check run to a quality gate tool.
     */
    private QualityGateTool matchCheckRun(String name, String appSlug) {
        // Priority 1: App slug (most reliable - direct GitHub App integration)
        if (appSlug != null) {
            Optional<QualityGateTool> match = QualityGateTool.matchAppSlug(appSlug);
            if (match.isPresent()) {
                return match.get();
            }
        }

        // Priority 2: Check run name patterns from enum
        if (name != null) {
            Optional<QualityGateTool> match = QualityGateTool.matchCheckRunName(name);
            if (match.isPresent()) {
                return match.get();
            }

            // Fallback keyword matching for compound check run names (e.g. "sonar-cloud-build")
            String lowerName = name.toLowerCase();
            for (Map.Entry<String, QualityGateTool> entry : QUICK_MATCH_KEYWORDS.entrySet()) {
                if (lowerName.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * Calculate confidence in check run match.
     */
    private double calculateCheckRunConfidence(String name, String appSlug, QualityGateTool tool) {
        // App slug match is highly reliable (direct integration)
        if (appSlug != null && tool.getCheckRunAppSlugs().stream()
                .anyMatch(s -> s.equalsIgnoreCase(appSlug))) {
            return 0.95;
        }

        // Exact tool name in check name
        if (name != null && name.toLowerCase().contains(tool.getDisplayName().toLowerCase())) {
            return 0.90;
        }

        // "Quality Gate" in name (usually Sonar)
        if (name != null && name.toLowerCase().contains("quality gate")) {
            return 0.85;
        }

        // Pattern match from enum
        return 0.75;
    }

    // COMMIT STATUS PARSING (External apps like SonarCloud, Codecov)

    @Override
    public CommitStatus parseAndMatchStatus(Map<String, Object> raw) {
        String context = (String) raw.get("context");
        String state = (String) raw.get("state");
        String description = (String) raw.get("description");
        String targetUrl = (String) raw.get("target_url");

        // Match to QG tool
        QualityGateTool matchedTool = matchStatusContext(context);
        double confidence = matchedTool != null ? calculateStatusConfidence(context, matchedTool) : 0.0;

        Long id = null;
        if (raw.get("id") != null) {
            id = ((Number) raw.get("id")).longValue();
        }

        return CommitStatus.builder()
                .id(id)
                .state(state)
                .context(context)
                .description(description)
                .targetUrl(targetUrl)
                .createdAt(parseInstant((String) raw.get("created_at")))
                .updatedAt(parseInstant((String) raw.get("updated_at")))
                .matchedTool(matchedTool)
                .matchConfidence(confidence)
                .build();
    }

    /**
     * Match a commit status context string to a quality gate tool.
     * <p>
     * Handles three types of contexts:
     * 1. Direct QG app contexts: "SonarCloud Code Analysis", "codecov/project"
     * 2. CI/CD providers running QG tools: "AWS CodeBuild (project-sonar)"
     * 3. Generic patterns via enum matching
     */
    private QualityGateTool matchStatusContext(String context) {
        if (context == null || context.isBlank()) {
            return null;
        }

        String lower = context.toLowerCase();

        // PASS 1: Quick match using keyword map (handles most common cases fast)
        for (Map.Entry<String, QualityGateTool> entry : QUICK_MATCH_KEYWORDS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // PASS 2: Check if this is a CI/CD provider context
        // These wrap QG tools in their own status names, e.g.:
        // "AWS CodeBuild us-west-2 (my-project-sonar-scan)"
        if (isCICDProvider(lower)) {
            // Extract the part in parentheses (if any) - often contains the job name
            String jobName = extractJobName(context);
            if (jobName != null && !jobName.isBlank()) {
                // Try to match the job name against QG tools
                QualityGateTool match = matchJobNameToTool(jobName.toLowerCase());
                if (match != null) {
                    return match;
                }
            }
        }

        // PASS 3: "Quality Gate" phrase (common in Sonar contexts)
        if (lower.contains("quality gate") || lower.contains("quality-gate")) {
            return QualityGateTool.SONARCLOUD;
        }

        // PASS 4: Fall back to enum's check run name patterns
        return QualityGateTool.matchCheckRunName(context).orElse(null);
    }

    /**
     * Check if context is from a CI/CD provider.
     */
    private boolean isCICDProvider(String lower) {
        return CICD_PROVIDERS.stream().anyMatch(lower::contains);
    }

    /**
     * Extract job name from CI/CD context.
     * Handles patterns like:
     * - "AWS CodeBuild us-west-2 (my-project-sonar)" -> "my-project-sonar"
     * - "CircleCI: build/sonar-scan" -> "build/sonar-scan"
     * - "Jenkins » SonarQube Analysis" -> "SonarQube Analysis"
     */
    private String extractJobName(String context) {
        // Pattern 1: Content in parentheses
        int parenStart = context.indexOf('(');
        int parenEnd = context.lastIndexOf(')');
        if (parenStart >= 0 && parenEnd > parenStart) {
            return context.substring(parenStart + 1, parenEnd);
        }

        // Pattern 2: Content after colon
        int colonPos = context.indexOf(':');
        if (colonPos >= 0 && colonPos < context.length() - 1) {
            return context.substring(colonPos + 1).trim();
        }

        // Pattern 3: Content after special separators
        for (String sep : List.of(" » ", " / ", " - ")) {
            int sepPos = context.indexOf(sep);
            if (sepPos >= 0 && sepPos < context.length() - sep.length()) {
                return context.substring(sepPos + sep.length()).trim();
            }
        }

        return null;
    }

    /**
     * Match a CI/CD job name to a QG tool.
     */
    private QualityGateTool matchJobNameToTool(String jobName) {
        // Use the quick match keywords on the job name
        for (Map.Entry<String, QualityGateTool> entry : QUICK_MATCH_KEYWORDS.entrySet()) {
            if (jobName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Try enum matching
        return QualityGateTool.matchCheckRunName(jobName).orElse(null);
    }

    /**
     * Calculate confidence for status context match.
     */
    private double calculateStatusConfidence(String context, QualityGateTool tool) {
        if (context == null) return 0.0;
        String lower = context.toLowerCase();

        // Direct tool name match (highest confidence)
        String toolName = tool.getDisplayName().toLowerCase();
        if (lower.contains(toolName)) {
            return 0.95;
        }

        // Well-known patterns
        if (tool == QualityGateTool.CODECOV && lower.startsWith("codecov/")) {
            return 0.95;
        }
        if (tool == QualityGateTool.SONARCLOUD && lower.contains("sonarcloud")) {
            return 0.95;
        }
        if (tool == QualityGateTool.SONARQUBE && lower.contains("sonarqube")) {
            return 0.95;
        }

        // "Quality Gate" phrase
        if (lower.contains("quality gate") &&
                (tool == QualityGateTool.SONARCLOUD || tool == QualityGateTool.SONARQUBE)) {
            return 0.90;
        }

        // CI/CD provider with embedded tool reference
        if (isCICDProvider(lower)) {
            String jobName = extractJobName(context);
            if (jobName != null && jobName.toLowerCase().contains(tool.name().toLowerCase().replace("_", ""))) {
                return 0.85;
            }
            // Generic "sonar" in CI/CD context
            if ((tool == QualityGateTool.SONARCLOUD || tool == QualityGateTool.SONARQUBE)
                    && lower.contains("sonar")) {
                return 0.85;
            }
        }

        // Keyword match (moderate confidence)
        return 0.75;
    }

    // HELPER METHODS

    @Override
    public List<CheckRun> filterQualityGateCheckRuns(List<CheckRun> checkRuns) {
        return checkRuns.stream()
                .filter(CheckRun::isQualityGateRelated)
                .toList();
    }

    @Override
    public List<CheckRun> filterThesisRelevant(List<CheckRun> checkRuns) {
        return checkRuns.stream()
                .filter(CheckRun::isThesisRelevant)
                .toList();
    }

    @Override
    public List<CheckRun> getFailedQGCheckRuns(List<CheckRun> checkRuns) {
        return checkRuns.stream()
                .filter(cr -> cr.isQualityGateRelated() && cr.isFailed())
                .toList();
    }

    @Override
    public List<CommitStatus> filterQualityGateStatuses(List<CommitStatus> statuses) {
        return statuses.stream()
                .filter(CommitStatus::isQualityGateRelated)
                .toList();
    }

    @Override
    public List<CommitStatus> getFailedQGStatuses(List<CommitStatus> statuses) {
        return statuses.stream()
                .filter(cs -> cs.isQualityGateRelated() && cs.isFailed())
                .toList();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "..." : s;
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
