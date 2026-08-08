package com.thesis.qualitygateanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Quality Gate Analyzer — entry point.
 * <p>
 * Thesis project: Analyzing the effectiveness of quality gate enforcement
 * in open-source GitHub repositories.
 * <p>
 * Analysis pipeline:
 * <p>
 * Phase 1 — Static configuration analysis
 * Scans workflow files, build configs, and dedicated quality gate config files.
 * Links build tool configurations to the workflows that invoke them.
 * <p>
 * Phase 2 — Workflow runs analysis
 * Retrieves runs for each detected quality gate workflow, filters to
 * PR-related runs, and identifies pull requests that had failures.
 * <p>
 * Phase 2.5 — Check run and commit status verification
 * Verifies that failures are quality-gate-related (not build/test failures).
 * Checks both GitHub Actions check runs and external app commit statuses
 * (SonarCloud, Codecov, etc.).
 * <p>
 * Phase 3 — PR outcome analysis
 * For each PR with a verified quality gate failure, determines the outcome:
 * FIXED_THEN_MERGED, BLOCKED, or MERGED_WITH_FAILURE.
 * <p>
 * Phase 4 — Enforcement scoring
 * Score = (fixed + blocked) / (fixed + blocked + merged_with_failure)
 * Thresholds: STRICTLY_ENFORCED >= 95%, MOSTLY_ENFORCED >= 80%,
 * PARTIALLY_ENFORCED >= 50%, NOT_ENFORCED < 50%.
 * <p>
 * Phase 5 — History analysis
 * Identifies when each quality gate tool was introduced, using the GitHub
 * GraphQL Blame API with a PR-based binary search fallback for external apps.
 * <p>
 * API base path: /api/v1
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.thesis.qualitygateanalyzer.repository")
public class QualityGateAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(QualityGateAnalyzerApplication.class, args);
    }
}
