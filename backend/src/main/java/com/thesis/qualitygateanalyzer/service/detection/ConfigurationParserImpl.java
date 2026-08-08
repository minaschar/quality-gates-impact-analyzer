package com.thesis.qualitygateanalyzer.service.detection;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.QualityGateDetection;
import com.thesis.qualitygateanalyzer.domain.qualitygate.QualityGateDetection.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default {@link ConfigurationParser} implementation.
 * Detects quality gates in workflows, build tools, and config files.
 */
@Slf4j
@Component
public class ConfigurationParserImpl implements ConfigurationParser {

    // Patterns for workflow analysis
    private static final Pattern PR_TRIGGER = Pattern.compile(
            "on:\\s*\\n.*?(pull_request|pull_request_target)\\s*:|" +
                    "on:\\s*\\[.*?(pull_request|pull_request_target).*?]",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CONTINUE_ON_ERROR = Pattern.compile(
            "continue-on-error\\s*:\\s*true",
            Pattern.CASE_INSENSITIVE
    );

    // Build commands that we look for in workflows
    private static final Map<String, List<String>> BUILD_COMMANDS = Map.of(
            "maven", List.of("mvn ", "mvn\n", "./mvnw", "maven"),
            "gradle", List.of("gradle ", "gradlew", "./gradlew"),
            "npm", List.of("npm run", "npm test", "npm ci", "yarn ", "pnpm "),
            "python", List.of("pip install", "pytest", "python -m", "poetry ", "tox"),
            "go", List.of("go build", "go test", "go vet"),
            "cargo", List.of("cargo build", "cargo test", "cargo clippy")
    );

    // WORKFLOW PARSING

    @Override
    public WorkflowParseResult parseWorkflow(String filePath, String content) {
        if (content == null || content.isBlank()) {
            return new WorkflowParseResult(Collections.emptyList(), false, Collections.emptyList());
        }

        List<QualityGateDetection> detections = new ArrayList<>();
        String lower = content.toLowerCase();

        // Check if triggers on PR
        boolean triggersOnPR = PR_TRIGGER.matcher(content).find();
        boolean hasContinueOnError = CONTINUE_ON_ERROR.matcher(content).find();

        // Find build commands used in this workflow
        List<String> buildCommandsFound = findBuildCommands(content);

        // Detect QG tools from GitHub Actions
        for (QualityGateTool tool : QualityGateTool.getThesisRelevant()) {
            // Check for action patterns (e.g., "uses: sonarsource/sonarcloud-github-action")
            for (String actionPattern : tool.getWorkflowActionPatterns()) {
                if (lower.contains(actionPattern.toLowerCase())) {
                    detections.add(createWorkflowDetection(
                            tool, filePath, SourceType.WORKFLOW_ACTION,
                            List.of("uses: " + actionPattern),
                            triggersOnPR, hasContinueOnError
                    ));
                    break; // Found this tool, move to next
                }
            }

            // Check for keyword patterns (e.g., "sonarcloud" anywhere)
            if (detections.stream().noneMatch(d -> d.getTool() == tool)) {
                for (String keyword : tool.getWorkflowKeywordPatterns()) {
                    if (lower.contains(keyword.toLowerCase())) {
                        detections.add(createWorkflowDetection(
                                tool, filePath, SourceType.WORKFLOW_COMMAND,
                                List.of("keyword: " + keyword),
                                triggersOnPR, hasContinueOnError
                        ));
                        break;
                    }
                }
            }

            // Check for build commands that run this tool
            if (detections.stream().noneMatch(d -> d.getTool() == tool)) {
                for (String cmd : tool.getBuildCommands()) {
                    if (lower.contains(cmd.toLowerCase())) {
                        detections.add(createWorkflowDetection(
                                tool, filePath, SourceType.WORKFLOW_COMMAND,
                                List.of("command: " + cmd),
                                triggersOnPR, hasContinueOnError
                        ));
                        break;
                    }
                }
            }
        }

        return new WorkflowParseResult(detections, triggersOnPR, buildCommandsFound);
    }

    // BUILD TOOL PARSING

    @Override
    public List<QualityGateDetection> parseBuildConfig(String filePath, String content,
                                                       String associatedWorkflow) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        String fileName = filePath.toLowerCase();

        if (fileName.endsWith("pom.xml")) {
            return parseMavenPom(filePath, content, associatedWorkflow);
        } else if (fileName.contains("build.gradle")) {
            return parseGradleBuild(filePath, content, associatedWorkflow);
        } else if (fileName.endsWith("package.json")) {
            return parsePackageJson(filePath, content, associatedWorkflow);
        } else if (fileName.endsWith("pyproject.toml") || fileName.endsWith("setup.cfg")) {
            return parsePythonConfig(filePath, content, associatedWorkflow);
        } else if (fileName.endsWith("go.mod")) {
            return parseGoMod(filePath, content, associatedWorkflow);
        } else if (fileName.endsWith("cargo.toml")) {
            return parseCargoToml(filePath, content, associatedWorkflow);
        } else if (fileName.endsWith("gemfile")) {
            return parseGemfile(filePath, content, associatedWorkflow);
        } else if (fileName.endsWith("composer.json")) {
            return parseComposerJson(filePath, content, associatedWorkflow);
        }

        return Collections.emptyList();
    }

    /**
     * Parse Maven pom.xml.
     */
    private List<QualityGateDetection> parseMavenPom(String filePath, String content, String workflow) {
        List<QualityGateDetection> detections = new ArrayList<>();
        String lower = content.toLowerCase();

        // Checkstyle
        if (lower.contains("maven-checkstyle-plugin") || lower.contains("checkstyle")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("maven-checkstyle-plugin");
            if (content.contains("failsOnViolation>true<") || content.contains("failOnViolation>true<")) {
                evidence.add("failsOnViolation=true (enforcing)");
            }
            detections.add(createBuildToolDetection(QualityGateTool.CHECKSTYLE, filePath, evidence, workflow));
        }

        // PMD
        if (lower.contains("maven-pmd-plugin")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("maven-pmd-plugin");
            if (content.contains("failOnViolation>true<")) {
                evidence.add("failOnViolation=true (enforcing)");
            }
            detections.add(createBuildToolDetection(QualityGateTool.PMD, filePath, evidence, workflow));
        }

        // SpotBugs
        if (lower.contains("spotbugs-maven-plugin") || lower.contains("findbugs-maven-plugin")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("spotbugs-maven-plugin");
            if (content.contains("failOnError>true<")) {
                evidence.add("failOnError=true (enforcing)");
            }
            detections.add(createBuildToolDetection(QualityGateTool.SPOTBUGS, filePath, evidence, workflow));
        }

        // JaCoCo
        if (lower.contains("jacoco-maven-plugin")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("jacoco-maven-plugin");
            if (lower.contains("<minimum>")) {
                Pattern p = Pattern.compile("<minimum>([0-9.]+)</minimum>");
                Matcher m = p.matcher(content);
                if (m.find()) {
                    evidence.add("coverage minimum=" + m.group(1) + " (enforcing)");
                }
            }
            detections.add(createBuildToolDetection(QualityGateTool.JACOCO, filePath, evidence, workflow));
        }

        // SonarQube
        if (lower.contains("sonar-maven-plugin") || lower.contains("sonar")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("sonar-maven-plugin");
            if (lower.contains("sonar.qualitygate.wait") && lower.contains("true")) {
                evidence.add("sonar.qualitygate.wait=true (enforcing)");
            }
            detections.add(createBuildToolDetection(QualityGateTool.SONARQUBE, filePath, evidence, workflow));
        }

        return detections;
    }

    /**
     * Parse Gradle build file.
     */
    private List<QualityGateDetection> parseGradleBuild(String filePath, String content, String workflow) {
        List<QualityGateDetection> detections = new ArrayList<>();
        String lower = content.toLowerCase();

        // Checkstyle
        if (lower.contains("checkstyle") && (lower.contains("id 'checkstyle'") || lower.contains("id(\"checkstyle\")"))) {
            List<String> evidence = List.of("checkstyle plugin applied");
            detections.add(createBuildToolDetection(QualityGateTool.CHECKSTYLE, filePath, evidence, workflow));
        }

        // SpotBugs
        if (lower.contains("spotbugs") || lower.contains("com.github.spotbugs")) {
            List<String> evidence = List.of("spotbugs plugin applied");
            detections.add(createBuildToolDetection(QualityGateTool.SPOTBUGS, filePath, evidence, workflow));
        }

        // JaCoCo
        if (lower.contains("jacoco")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("jacoco plugin");
            if (lower.contains("minimum")) {
                evidence.add("coverage threshold configured");
            }
            detections.add(createBuildToolDetection(QualityGateTool.JACOCO, filePath, evidence, workflow));
        }

        // SonarQube
        if (lower.contains("org.sonarqube") || lower.contains("sonarqube")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("sonarqube plugin");
            if (lower.contains("sonar.qualitygate.wait") && lower.contains("true")) {
                evidence.add("sonar.qualitygate.wait=true (enforcing)");
            }
            detections.add(createBuildToolDetection(QualityGateTool.SONARQUBE, filePath, evidence, workflow));
        }

        // Detekt (Kotlin)
        if (lower.contains("detekt") || lower.contains("io.gitlab.arturbosch.detekt")) {
            detections.add(createBuildToolDetection(QualityGateTool.DETEKT, filePath,
                    List.of("detekt plugin"), workflow));
        }

        // Ktlint
        if (lower.contains("ktlint") || lower.contains("org.jlleitschuh.gradle.ktlint")) {
            detections.add(createBuildToolDetection(QualityGateTool.KTLINT, filePath,
                    List.of("ktlint plugin"), workflow));
        }

        return detections;
    }

    /**
     * Parse package.json for JS/TS projects.
     */
    private List<QualityGateDetection> parsePackageJson(String filePath, String content, String workflow) {
        List<QualityGateDetection> detections = new ArrayList<>();
        String lower = content.toLowerCase();

        // ESLint
        if (lower.contains("eslint")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("eslint dependency");
            if (lower.contains("--max-warnings=0") || lower.contains("--max-warnings 0")) {
                evidence.add("--max-warnings=0 (enforcing)");
            }
            detections.add(createBuildToolDetection(QualityGateTool.ESLINT, filePath, evidence, workflow));
        }

        // Prettier
        if (lower.contains("prettier")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("prettier dependency");
            if (lower.contains("--check") || lower.contains("prettier:check")) {
                evidence.add("check mode (enforcing)");
            }
            detections.add(createBuildToolDetection(QualityGateTool.PRETTIER, filePath, evidence, workflow));
        }

        // Jest coverage
        if (lower.contains("jest") && lower.contains("coverage")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("jest with coverage");
            if (lower.contains("coveragethreshold")) {
                evidence.add("coverage threshold (enforcing)");
            }
            detections.add(createBuildToolDetection(QualityGateTool.ISTANBUL, filePath, evidence, workflow));
        }

        return detections;
    }

    /**
     * Parse Python config files.
     */
    private List<QualityGateDetection> parsePythonConfig(String filePath, String content, String workflow) {
        List<QualityGateDetection> detections = new ArrayList<>();
        String lower = content.toLowerCase();

        // Ruff
        if (lower.contains("[tool.ruff]") || lower.contains("ruff")) {
            detections.add(createBuildToolDetection(QualityGateTool.RUFF, filePath,
                    List.of("ruff configured"), workflow));
        }

        // Black
        if (lower.contains("[tool.black]")) {
            detections.add(createBuildToolDetection(QualityGateTool.BLACK, filePath,
                    List.of("black configured"), workflow));
        }

        // MyPy
        if (lower.contains("[tool.mypy]") || lower.contains("[mypy]")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("mypy configured");
            if (lower.contains("strict = true") || lower.contains("strict=true")) {
                evidence.add("strict mode (enforcing)");
            }
            detections.add(createBuildToolDetection(QualityGateTool.MYPY, filePath, evidence, workflow));
        }

        // Pylint
        if (lower.contains("[tool.pylint]") || lower.contains("pylint")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("pylint configured");
            Pattern p = Pattern.compile("fail[_-]under\\s*=\\s*([0-9.]+)");
            Matcher m = p.matcher(lower);
            if (m.find()) {
                evidence.add("fail-under=" + m.group(1) + " (enforcing)");
            }
            detections.add(createBuildToolDetection(QualityGateTool.PYLINT, filePath, evidence, workflow));
        }

        // Coverage.py
        if (lower.contains("fail_under") || lower.contains("[tool.coverage")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("coverage.py configured");
            Pattern p = Pattern.compile("fail_under\\s*=\\s*(\\d+)");
            Matcher m = p.matcher(lower);
            if (m.find()) {
                evidence.add("fail_under=" + m.group(1) + " (enforcing)");
            }
            detections.add(createBuildToolDetection(QualityGateTool.COVERAGE_PY, filePath, evidence, workflow));
        }

        return detections;
    }

    /**
     * Parse Go module.
     */
    private List<QualityGateDetection> parseGoMod(String filePath, String content, String workflow) {
        // Go mod itself doesn't contain QG config, but we note it as a Go project
        return Collections.emptyList();
    }

    /**
     * Parse Cargo.toml for Rust projects.
     */
    private List<QualityGateDetection> parseCargoToml(String filePath, String content, String workflow) {
        List<QualityGateDetection> detections = new ArrayList<>();
        String lower = content.toLowerCase();

        if (lower.contains("[lints]") || lower.contains("clippy")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("clippy lints configured");
            if (lower.contains("deny") || lower.contains("forbid")) {
                evidence.add("deny/forbid level (enforcing)");
            }
            detections.add(createBuildToolDetection(QualityGateTool.CLIPPY, filePath, evidence, workflow));
        }

        return detections;
    }

    /**
     * Parse Ruby Gemfile.
     */
    private List<QualityGateDetection> parseGemfile(String filePath, String content, String workflow) {
        List<QualityGateDetection> detections = new ArrayList<>();
        String lower = content.toLowerCase();

        if (lower.contains("rubocop")) {
            detections.add(createBuildToolDetection(QualityGateTool.RUBOCOP, filePath,
                    List.of("rubocop gem"), workflow));
        }

        return detections;
    }

    /**
     * Parse PHP composer.json.
     */
    private List<QualityGateDetection> parseComposerJson(String filePath, String content, String workflow) {
        List<QualityGateDetection> detections = new ArrayList<>();
        String lower = content.toLowerCase();

        if (lower.contains("phpstan")) {
            detections.add(createBuildToolDetection(QualityGateTool.PHPSTAN, filePath,
                    List.of("phpstan dependency"), workflow));
        }

        if (lower.contains("phpcs") || lower.contains("php_codesniffer")) {
            detections.add(createBuildToolDetection(QualityGateTool.PHPCS, filePath,
                    List.of("phpcs dependency"), workflow));
        }

        if (lower.contains("psalm")) {
            detections.add(createBuildToolDetection(QualityGateTool.PSALM, filePath,
                    List.of("psalm dependency"), workflow));
        }

        return detections;
    }

    // DEDICATED CONFIG FILES

    @Override
    public Optional<QualityGateDetection> parseConfigFile(String filePath, String content) {
        if (content == null) return Optional.empty();

        String fileName = filePath.toLowerCase();
        String lower = content.toLowerCase();

        // SonarQube/SonarCloud properties
        if (fileName.contains("sonar")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("sonar properties file");
            if (lower.contains("sonar.qualitygate.wait=true")) {
                evidence.add("sonar.qualitygate.wait=true (enforcing)");
            }
            QualityGateTool tool = fileName.contains("sonarcloud") ?
                    QualityGateTool.SONARCLOUD : QualityGateTool.SONARQUBE;
            return Optional.of(createConfigFileDetection(tool, filePath, evidence));
        }

        // Codecov
        if (fileName.contains("codecov")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("codecov config");
            if (lower.contains("threshold") || lower.contains("target")) {
                evidence.add("coverage thresholds (enforcing)");
            }
            return Optional.of(createConfigFileDetection(QualityGateTool.CODECOV, filePath, evidence));
        }

        // ESLint config
        if (fileName.contains("eslint")) {
            return Optional.of(createConfigFileDetection(QualityGateTool.ESLINT, filePath,
                    List.of("eslint config file")));
        }

        // Checkstyle
        if (fileName.contains("checkstyle")) {
            return Optional.of(createConfigFileDetection(QualityGateTool.CHECKSTYLE, filePath,
                    List.of("checkstyle config file")));
        }

        // Codacy
        if (fileName.contains("codacy")) {
            return Optional.of(createConfigFileDetection(QualityGateTool.CODACY, filePath,
                    List.of("codacy config file")));
        }

        // Code Climate
        if (fileName.contains("codeclimate")) {
            return Optional.of(createConfigFileDetection(QualityGateTool.CODE_CLIMATE, filePath,
                    List.of("codeclimate config file")));
        }

        // GolangCI-Lint
        if (fileName.contains("golangci")) {
            return Optional.of(createConfigFileDetection(QualityGateTool.GOLANGCI_LINT, filePath,
                    List.of("golangci-lint config file")));
        }

        // RuboCop
        if (fileName.contains("rubocop")) {
            return Optional.of(createConfigFileDetection(QualityGateTool.RUBOCOP, filePath,
                    List.of("rubocop config file")));
        }

        // PHPStan
        if (fileName.contains("phpstan")) {
            List<String> evidence = new ArrayList<>();
            evidence.add("phpstan config");
            if (lower.contains("level: max") || lower.contains("level: 8") || lower.contains("level: 9")) {
                evidence.add("high analysis level (enforcing)");
            }
            return Optional.of(createConfigFileDetection(QualityGateTool.PHPSTAN, filePath, evidence));
        }

        return Optional.empty();
    }

    // HELPERS

    private List<String> findBuildCommands(String content) {
        List<String> found = new ArrayList<>();
        String lower = content.toLowerCase();

        for (Map.Entry<String, List<String>> entry : BUILD_COMMANDS.entrySet()) {
            for (String cmd : entry.getValue()) {
                if (lower.contains(cmd.toLowerCase())) {
                    found.add(entry.getKey());
                    break;
                }
            }
        }

        return found;
    }

    private QualityGateDetection createWorkflowDetection(QualityGateTool tool, String filePath,
                                                         SourceType sourceType, List<String> evidence,
                                                         boolean triggersOnPR, boolean hasContinueOnError) {
        double confidence = calculateWorkflowConfidence(sourceType, evidence, triggersOnPR, hasContinueOnError);

        List<String> allEvidence = new ArrayList<>(evidence);
        if (triggersOnPR) allEvidence.add("triggers on PR");
        if (hasContinueOnError) allEvidence.add("WARNING: continue-on-error:true");

        return QualityGateDetection.builder()
                .tool(tool)
                .category(tool.getCategory())
                .sourceFile(filePath)
                .sourceType(sourceType)
                .evidenceFound(allEvidence)
                .confidenceScore(confidence)
                .triggersOnPR(triggersOnPR)
                .associatedWorkflow(null)
                .build();
    }

    private QualityGateDetection createBuildToolDetection(QualityGateTool tool, String filePath,
                                                          List<String> evidence, String workflow) {
        boolean hasEnforcement = evidence.stream().anyMatch(e -> e.contains("enforcing"));
        double confidence = hasEnforcement ? 0.85 : 0.70;

        return QualityGateDetection.builder()
                .tool(tool)
                .category(tool.getCategory())
                .sourceFile(filePath)
                .sourceType(SourceType.BUILD_TOOL)
                .evidenceFound(evidence)
                .confidenceScore(confidence)
                .triggersOnPR(null) // Depends on workflow
                .associatedWorkflow(workflow)
                .build();
    }

    private QualityGateDetection createConfigFileDetection(QualityGateTool tool, String filePath,
                                                           List<String> evidence) {
        boolean hasEnforcement = evidence.stream().anyMatch(e -> e.contains("enforcing"));
        double confidence = hasEnforcement ? 0.80 : 0.65;

        return QualityGateDetection.builder()
                .tool(tool)
                .category(tool.getCategory())
                .sourceFile(filePath)
                .sourceType(SourceType.CONFIG_FILE)
                .evidenceFound(evidence)
                .confidenceScore(confidence)
                .triggersOnPR(null)
                .associatedWorkflow(null)
                .build();
    }

    private double calculateWorkflowConfidence(SourceType sourceType, List<String> evidence,
                                               boolean triggersOnPR, boolean hasContinueOnError) {
        double score = sourceType == SourceType.WORKFLOW_ACTION ? 0.80 : 0.60;

        if (triggersOnPR) score += 0.10;
        if (evidence.stream().anyMatch(e -> e.contains("enforcing"))) score += 0.10;
        if (hasContinueOnError) score -= 0.25;

        return Math.clamp(score, 0.20, 1.0);
    }
}
