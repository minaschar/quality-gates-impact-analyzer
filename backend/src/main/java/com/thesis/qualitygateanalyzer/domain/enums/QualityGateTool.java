package com.thesis.qualitygateanalyzer.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comprehensive enumeration of all quality gate tools.
 * Includes detection patterns for workflows, build tools, config files, and check runs.
 */
@Getter
@RequiredArgsConstructor
public enum QualityGateTool {

    // CODE QUALITY - Primary focus for thesis (bugs, code smells, maintainability)

    SONARQUBE(
            "SonarQube",
            QualityGateCategory.CODE_QUALITY,
            // Workflow action patterns
            List.of("sonarqube-scan-action", "sonar-scanner", "sonarsource/sonarqube"),
            // Workflow keyword patterns (broader)
            List.of("sonarqube", "sonar-scanner", "sonar.host.url"),
            // Build tool patterns
            List.of("sonar-maven-plugin", "org.sonarqube", "sonarqube"),
            // Config file names
            List.of("sonar-project.properties"),
            // Check run name patterns (for Check Runs API)
            List.of("sonarqube"),
            // Check run app slugs (for Check Runs API)
            List.of(),
            // Build commands that trigger this tool
            List.of("mvn sonar:sonar", "gradle sonarqube", "sonar-scanner")
    ),

    SONARCLOUD(
            "SonarCloud",
            QualityGateCategory.CODE_QUALITY,
            List.of("sonarsource/sonarcloud-github-action", "SonarSource/sonarcloud-github-action"),
            List.of("sonarcloud", "sonar.organization"),
            List.of("sonar-maven-plugin"),
            List.of("sonar-project.properties", ".sonarcloud.properties"),
            List.of("sonarcloud", "quality gate"),
            List.of("sonarcloud", "sonarqubecloud", "sonar-cloud"),
            List.of("mvn sonar:sonar", "gradle sonarqube")
    ),

    CODACY(
            "Codacy",
            QualityGateCategory.CODE_QUALITY,
            List.of("codacy/codacy-analysis-cli-action", "codacy/codacy-coverage-reporter-action"),
            List.of("codacy"),
            List.of(),
            List.of(".codacy.yml", ".codacy.yaml", "codacy.yml"),
            List.of("codacy"),
            List.of("codacy"),
            List.of()
    ),

    CODE_CLIMATE(
            "Code Climate",
            QualityGateCategory.CODE_QUALITY,
            List.of("paambaati/codeclimate-action", "codeclimate/codeclimate-action"),
            List.of("codeclimate", "code-climate", "cc-test-reporter"),
            List.of(),
            List.of(".codeclimate.yml", ".codeclimate.json"),
            List.of("codeclimate", "code climate"),
            List.of("codeclimate"),
            List.of()
    ),

    QODANA(
            "Qodana",
            QualityGateCategory.CODE_QUALITY,
            List.of("jetbrains/qodana-action", "JetBrains/qodana-action"),
            List.of("qodana"),
            List.of(),
            List.of("qodana.yaml", "qodana.yml"),
            List.of("qodana"),
            List.of("qodana"),
            List.of()
    ),

    DEEPSOURCE(
            "DeepSource",
            QualityGateCategory.CODE_QUALITY,
            List.of("deepsourcelabs/test-coverage-action"),
            List.of("deepsource"),
            List.of(),
            List.of(".deepsource.toml"),
            List.of("deepsource"),
            List.of("deepsource"),
            List.of()
    ),

    // CODE STYLE - Linting, formatting, conventions

    CHECKSTYLE(
            "Checkstyle",
            QualityGateCategory.CODE_STYLE,
            List.of("checkstyle/checkstyle-action", "nikitasavinov/checkstyle-action"),
            List.of("checkstyle"),
            List.of("maven-checkstyle-plugin", "checkstyle", "org.checkstyle"),
            List.of("checkstyle.xml", "checkstyle-config.xml", "google_checks.xml", "sun_checks.xml"),
            List.of("checkstyle"),
            List.of(),
            List.of("mvn checkstyle:check", "gradle checkstyleMain")
    ),

    PMD(
            "PMD",
            QualityGateCategory.CODE_STYLE,
            List.of("pmd/pmd-github-action"),
            List.of("pmd"),
            List.of("maven-pmd-plugin", "pmd"),
            List.of("pmd-ruleset.xml", "pmd.xml", "ruleset.xml"),
            List.of("pmd"),
            List.of(),
            List.of("mvn pmd:check", "gradle pmdMain")
    ),

    SPOTBUGS(
            "SpotBugs",
            QualityGateCategory.CODE_STYLE,
            List.of("spotbugs/spotbugs-github-action"),
            List.of("spotbugs", "findbugs"),
            List.of("spotbugs-maven-plugin", "com.github.spotbugs", "findbugs-maven-plugin"),
            List.of("spotbugs-exclude.xml", "spotbugs-include.xml", "findbugs-exclude.xml"),
            List.of("spotbugs", "findbugs"),
            List.of(),
            List.of("mvn spotbugs:check", "gradle spotbugsMain")
    ),

    ESLINT(
            "ESLint",
            QualityGateCategory.CODE_STYLE,
            List.of("reviewdog/action-eslint", "eslint/eslint"),
            List.of("eslint"),
            List.of("eslint"),
            List.of(".eslintrc", ".eslintrc.js", ".eslintrc.json", ".eslintrc.yml", "eslint.config.js", "eslint.config.mjs"),
            List.of("eslint"),
            List.of(),
            List.of("npm run lint", "npx eslint", "yarn lint")
    ),

    PRETTIER(
            "Prettier",
            QualityGateCategory.CODE_STYLE,
            List.of("creyD/prettier_action"),
            List.of("prettier"),
            List.of("prettier"),
            List.of(".prettierrc", ".prettierrc.js", ".prettierrc.json", "prettier.config.js"),
            List.of("prettier"),
            List.of(),
            List.of("npm run format", "npx prettier", "prettier --check")
    ),

    KTLINT(
            "ktlint",
            QualityGateCategory.CODE_STYLE,
            List.of(),
            List.of("ktlint"),
            List.of("ktlint", "org.jlleitschuh.gradle.ktlint"),
            List.of(".editorconfig"),
            List.of("ktlint"),
            List.of(),
            List.of("gradle ktlintCheck")
    ),

    DETEKT(
            "detekt",
            QualityGateCategory.CODE_STYLE,
            List.of("natiginfo/action-detekt-all"),
            List.of("detekt"),
            List.of("detekt", "io.gitlab.arturbosch.detekt"),
            List.of("detekt.yml", "detekt-config.yml"),
            List.of("detekt"),
            List.of(),
            List.of("gradle detekt")
    ),

    PYLINT(
            "Pylint",
            QualityGateCategory.CODE_STYLE,
            List.of(),
            List.of("pylint"),
            List.of("pylint"),
            List.of(".pylintrc", "pylintrc"),
            List.of("pylint"),
            List.of(),
            List.of("pylint", "python -m pylint")
    ),

    FLAKE8(
            "Flake8",
            QualityGateCategory.CODE_STYLE,
            List.of(),
            List.of("flake8"),
            List.of("flake8"),
            List.of(".flake8", "setup.cfg", "tox.ini"),
            List.of("flake8"),
            List.of(),
            List.of("flake8", "python -m flake8")
    ),

    RUFF(
            "Ruff",
            QualityGateCategory.CODE_STYLE,
            List.of("chartboost/ruff-action", "astral-sh/ruff-action"),
            List.of("ruff"),
            List.of("ruff"),
            List.of("ruff.toml", ".ruff.toml", "pyproject.toml"),
            List.of("ruff"),
            List.of(),
            List.of("ruff check", "ruff format")
    ),

    BLACK(
            "Black",
            QualityGateCategory.CODE_STYLE,
            List.of("psf/black", "rickstaa/action-black"),
            List.of("black"),
            List.of("black"),
            List.of("pyproject.toml"),
            List.of("black"),
            List.of(),
            List.of("black --check", "python -m black")
    ),

    MYPY(
            "MyPy",
            QualityGateCategory.CODE_STYLE,
            List.of(),
            List.of("mypy"),
            List.of("mypy"),
            List.of("mypy.ini", ".mypy.ini", "pyproject.toml"),
            List.of("mypy"),
            List.of(),
            List.of("mypy", "python -m mypy")
    ),

    GOLANGCI_LINT(
            "GolangCI-Lint",
            QualityGateCategory.CODE_STYLE,
            List.of("golangci/golangci-lint-action"),
            List.of("golangci-lint", "golangci"),
            List.of(),
            List.of(".golangci.yml", ".golangci.yaml", ".golangci.toml"),
            List.of("golangci", "golint"),
            List.of(),
            List.of("golangci-lint run")
    ),

    RUBOCOP(
            "RuboCop",
            QualityGateCategory.CODE_STYLE,
            List.of("reviewdog/action-rubocop"),
            List.of("rubocop"),
            List.of("rubocop"),
            List.of(".rubocop.yml", ".rubocop_todo.yml"),
            List.of("rubocop"),
            List.of(),
            List.of("rubocop", "bundle exec rubocop")
    ),

    PHPCS(
            "PHP_CodeSniffer",
            QualityGateCategory.CODE_STYLE,
            List.of("php-actions/phpcs"),
            List.of("phpcs", "php_codesniffer"),
            List.of("squizlabs/php_codesniffer"),
            List.of("phpcs.xml", "phpcs.xml.dist", ".phpcs.xml"),
            List.of("phpcs", "codesniffer"),
            List.of(),
            List.of("phpcs", "vendor/bin/phpcs")
    ),

    PHPSTAN(
            "PHPStan",
            QualityGateCategory.CODE_STYLE,
            List.of("php-actions/phpstan"),
            List.of("phpstan"),
            List.of("phpstan/phpstan"),
            List.of("phpstan.neon", "phpstan.neon.dist"),
            List.of("phpstan"),
            List.of(),
            List.of("phpstan analyse", "vendor/bin/phpstan")
    ),

    PSALM(
            "Psalm",
            QualityGateCategory.CODE_STYLE,
            List.of("php-actions/psalm"),
            List.of("psalm"),
            List.of("vimeo/psalm"),
            List.of("psalm.xml", "psalm.xml.dist"),
            List.of("psalm"),
            List.of(),
            List.of("psalm", "vendor/bin/psalm")
    ),

    CLIPPY(
            "Clippy",
            QualityGateCategory.CODE_STYLE,
            List.of("actions-rs/clippy-check", "giraffate/clippy-action"),
            List.of("clippy"),
            List.of(),
            List.of("clippy.toml", ".clippy.toml"),
            List.of("clippy"),
            List.of(),
            List.of("cargo clippy")
    ),

    RUSTFMT(
            "Rustfmt",
            QualityGateCategory.CODE_STYLE,
            List.of(),
            List.of("rustfmt", "cargo fmt"),
            List.of(),
            List.of("rustfmt.toml", ".rustfmt.toml"),
            List.of("rustfmt", "cargo fmt"),
            List.of(),
            List.of("cargo fmt --check")
    ),

    SWIFTLINT(
            "SwiftLint",
            QualityGateCategory.CODE_STYLE,
            List.of("norio-nomura/action-swiftlint"),
            List.of("swiftlint"),
            List.of(),
            List.of(".swiftlint.yml", ".swiftlint.yaml"),
            List.of("swiftlint"),
            List.of(),
            List.of("swiftlint")
    ),

    // COVERAGE - Test coverage measurement and enforcement

    CODECOV(
            "Codecov",
            QualityGateCategory.COVERAGE,
            List.of("codecov/codecov-action"),
            List.of("codecov"),
            List.of(),
            List.of("codecov.yml", ".codecov.yml", "codecov.yaml"),
            List.of("codecov", "coverage"),
            List.of("codecov"),
            List.of()
    ),

    COVERALLS(
            "Coveralls",
            QualityGateCategory.COVERAGE,
            List.of("coverallsapp/github-action"),
            List.of("coveralls"),
            List.of(),
            List.of(".coveralls.yml"),
            List.of("coveralls"),
            List.of("coveralls"),
            List.of()
    ),

    JACOCO(
            "JaCoCo",
            QualityGateCategory.COVERAGE,
            List.of("madrapps/jacoco-report"),
            List.of("jacoco"),
            List.of("jacoco-maven-plugin", "jacoco", "org.jacoco"),
            List.of(),
            List.of("jacoco", "coverage"),
            List.of(),
            List.of("mvn jacoco:check", "gradle jacocoTestCoverageVerification")
    ),

    COBERTURA(
            "Cobertura",
            QualityGateCategory.COVERAGE,
            List.of(),
            List.of("cobertura"),
            List.of("cobertura-maven-plugin", "cobertura"),
            List.of(),
            List.of("cobertura"),
            List.of(),
            List.of()
    ),

    ISTANBUL(
            "Istanbul/NYC",
            QualityGateCategory.COVERAGE,
            List.of(),
            List.of("nyc", "istanbul", "c8"),
            List.of("nyc", "istanbul", "c8"),
            List.of(".nycrc", ".nycrc.json", "nyc.config.js", ".c8rc"),
            List.of("coverage"),
            List.of(),
            List.of("nyc", "c8")
    ),

    COVERAGE_PY(
            "Coverage.py",
            QualityGateCategory.COVERAGE,
            List.of(),
            List.of("coverage", "pytest-cov"),
            List.of("coverage", "pytest-cov"),
            List.of(".coveragerc", "pyproject.toml"),
            List.of("coverage"),
            List.of(),
            List.of("coverage run", "pytest --cov")
    ),

    // SECURITY - Not primary focus but tracked for completeness

    SNYK(
            "Snyk",
            QualityGateCategory.SECURITY,
            List.of("snyk/actions"),
            List.of("snyk"),
            List.of(),
            List.of(".snyk"),
            List.of("snyk"),
            List.of("snyk"),
            List.of()
    ),

    TRIVY(
            "Trivy",
            QualityGateCategory.SECURITY,
            List.of("aquasecurity/trivy-action"),
            List.of("trivy"),
            List.of(),
            List.of("trivy.yaml"),
            List.of("trivy"),
            List.of(),
            List.of()
    ),

    DEPENDABOT(
            "Dependabot",
            QualityGateCategory.SECURITY,
            List.of(),
            List.of("dependabot"),
            List.of(),
            List.of(".github/dependabot.yml", ".github/dependabot.yaml"),
            List.of("dependabot"),
            List.of("dependabot"),
            List.of()
    ),

    SEMGREP(
            "Semgrep",
            QualityGateCategory.SECURITY,
            List.of("returntocorp/semgrep-action", "semgrep/semgrep-action"),
            List.of("semgrep"),
            List.of(),
            List.of(".semgrep.yml", ".semgrep.yaml", "semgrep.yml"),
            List.of("semgrep"),
            List.of(),
            List.of()
    );

    private final String displayName;
    private final QualityGateCategory category;
    private final List<String> workflowActionPatterns;    // Official GitHub actions
    private final List<String> workflowKeywordPatterns;   // Keywords in workflow files
    private final List<String> buildToolPatterns;         // Patterns in pom.xml, build.gradle, etc.
    private final List<String> configFileNames;           // Dedicated config files
    private final List<String> checkRunNamePatterns;      // Patterns in check run names
    private final List<String> checkRunAppSlugs;          // GitHub App slugs
    private final List<String> buildCommands;             // Commands that run this tool

    /**
     * Get all thesis-relevant tools (CODE_QUALITY, CODE_STYLE, COVERAGE).
     */
    public static Set<QualityGateTool> getThesisRelevant() {
        return Arrays.stream(values())
                .filter(t -> t.category.isRelevantForThesis())
                .collect(Collectors.toSet());
    }

    /**
     * Get tools by category.
     */
    public static Set<QualityGateTool> getByCategory(QualityGateCategory category) {
        return Arrays.stream(values())
                .filter(t -> t.category == category)
                .collect(Collectors.toSet());
    }

    /**
     * Try to match a check run name to a tool.
     */
    public static Optional<QualityGateTool> matchCheckRunName(String name) {
        if (name == null) return Optional.empty();
        String lower = name.toLowerCase();

        for (QualityGateTool tool : values()) {
            for (String pattern : tool.checkRunNamePatterns) {
                if (lower.contains(pattern.toLowerCase())) {
                    return Optional.of(tool);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Try to match a check run app slug to a tool.
     */
    public static Optional<QualityGateTool> matchAppSlug(String slug) {
        if (slug == null) return Optional.empty();
        String lower = slug.toLowerCase();

        for (QualityGateTool tool : values()) {
            if (tool.checkRunAppSlugs.contains(lower)) {
                return Optional.of(tool);
            }
        }
        return Optional.empty();
    }

    public boolean isRelevantForThesis() {
        return category.isRelevantForThesis();
    }
}
