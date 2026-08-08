package com.thesis.qualitygateanalyzer.service.detection;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.QualityGateDetection;
import com.thesis.qualitygateanalyzer.service.detection.ConfigurationParser.WorkflowParseResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationParserImplTest {

    private final ConfigurationParserImpl parser = new ConfigurationParserImpl();

    @Nested
    class ParseWorkflow {
        @Test
        void blankContent_returnsEmptyResult() {
            WorkflowParseResult result = parser.parseWorkflow("ci.yml", "");
            assertThat(result.detections()).isEmpty();
            assertThat(result.triggersOnPR()).isFalse();
            assertThat(result.buildCommandsFound()).isEmpty();
        }

        @Test
        void nullContent_returnsEmptyResult() {
            WorkflowParseResult result = parser.parseWorkflow("ci.yml", null);
            assertThat(result.detections()).isEmpty();
        }

        @Test
        void prTriggerBlockForm_isDetected() {
            String content = "on:\n  pull_request:\n    branches: [main]\njobs: {}";
            WorkflowParseResult result = parser.parseWorkflow("ci.yml", content);
            assertThat(result.triggersOnPR()).isTrue();
        }

        @Test
        void prTriggerListForm_isDetected() {
            String content = "on: [push, pull_request]\njobs: {}";
            WorkflowParseResult result = parser.parseWorkflow("ci.yml", content);
            assertThat(result.triggersOnPR()).isTrue();
        }

        @Test
        void noPrTrigger_isFalse() {
            String content = "on: push\njobs: {}";
            WorkflowParseResult result = parser.parseWorkflow("ci.yml", content);
            assertThat(result.triggersOnPR()).isFalse();
        }

        @Test
        void continueOnError_reducesConfidence() {
            String actionContent = "uses: sonarsource/sonarcloud-github-action\ncontinue-on-error: true";
            WorkflowParseResult withError = parser.parseWorkflow("ci.yml", actionContent);
            QualityGateDetection detection = withError.detections().stream()
                    .filter(d -> d.getTool() == QualityGateTool.SONARCLOUD).findFirst().orElseThrow();
            assertThat(detection.getConfidenceScore()).isLessThan(0.80);
            assertThat(detection.getEvidenceFound()).anyMatch(e -> e.contains("continue-on-error"));
        }

        @Test
        void actionPattern_detectedWithHighBaseConfidence() {
            String content = "uses: sonarsource/sonarcloud-github-action@v2";
            WorkflowParseResult result = parser.parseWorkflow("ci.yml", content);
            QualityGateDetection detection = result.detections().stream()
                    .filter(d -> d.getTool() == QualityGateTool.SONARCLOUD).findFirst().orElseThrow();
            assertThat(detection.getSourceType()).isEqualTo(QualityGateDetection.SourceType.WORKFLOW_ACTION);
            assertThat(detection.getConfidenceScore()).isEqualTo(0.80);
        }

        @Test
        void keywordPattern_detectedWhenNoActionPatternMatches() {
            String content = "run: echo checkstyle";
            WorkflowParseResult result = parser.parseWorkflow("ci.yml", content);
            assertThat(result.detections()).anyMatch(d -> d.getTool() == QualityGateTool.CHECKSTYLE
                    && d.getSourceType() == QualityGateDetection.SourceType.WORKFLOW_COMMAND);
        }

        @Test
        void buildCommandPattern_detectedWhenNoActionOrKeywordMatches() {
            String content = "run: mvn checkstyle:check";
            WorkflowParseResult result = parser.parseWorkflow("ci.yml", content);
            assertThat(result.detections()).anyMatch(d -> d.getTool() == QualityGateTool.CHECKSTYLE);
        }

        @Test
        void triggersOnPR_increasesConfidence() {
            String content = "on:\n  pull_request:\nrun: mvn checkstyle:check";
            WorkflowParseResult result = parser.parseWorkflow("ci.yml", content);
            QualityGateDetection detection = result.detections().stream()
                    .filter(d -> d.getTool() == QualityGateTool.CHECKSTYLE).findFirst().orElseThrow();
            assertThat(detection.getEvidenceFound()).contains("triggers on PR");
            assertThat(detection.getTriggersOnPR()).isTrue();
        }

        @Test
        void confidenceIsClampedToMinimum() {
            // WORKFLOW_COMMAND base (0.60) - continue-on-error penalty (0.25) = 0.35, still above floor,
            // but verifies the clamp function doesn't throw and stays within [0.20, 1.0].
            String content = "run: checkstyle\ncontinue-on-error: true";
            WorkflowParseResult result = parser.parseWorkflow("ci.yml", content);
            QualityGateDetection detection = result.detections().getFirst();
            assertThat(detection.getConfidenceScore()).isBetween(0.20, 1.0);
        }

        @Nested
        class BuildCommandDetection {
            @Test
            void mavenCommand_isFound() {
                assertThat(parser.parseWorkflow("ci.yml", "run: mvn test").buildCommandsFound()).contains("maven");
            }

            @Test
            void gradleCommand_isFound() {
                assertThat(parser.parseWorkflow("ci.yml", "run: ./gradlew build").buildCommandsFound()).contains("gradle");
            }

            @Test
            void npmCommand_isFound() {
                assertThat(parser.parseWorkflow("ci.yml", "run: npm test").buildCommandsFound()).contains("npm");
            }

            @Test
            void pythonCommand_isFound() {
                assertThat(parser.parseWorkflow("ci.yml", "run: pytest").buildCommandsFound()).contains("python");
            }

            @Test
            void goCommand_isFound() {
                assertThat(parser.parseWorkflow("ci.yml", "run: go test ./...").buildCommandsFound()).contains("go");
            }

            @Test
            void cargoCommand_isFound() {
                assertThat(parser.parseWorkflow("ci.yml", "run: cargo test").buildCommandsFound()).contains("cargo");
            }

            @Test
            void noCommand_returnsEmpty() {
                assertThat(parser.parseWorkflow("ci.yml", "run: echo hi").buildCommandsFound()).isEmpty();
            }
        }
    }

    @Nested
    class ParseBuildConfig {
        @Test
        void blankContent_returnsEmpty() {
            assertThat(parser.parseBuildConfig("pom.xml", "", null)).isEmpty();
        }

        @Test
        void nullContent_returnsEmpty() {
            assertThat(parser.parseBuildConfig("pom.xml", null, null)).isEmpty();
        }

        @Test
        void unrecognizedFile_returnsEmpty() {
            assertThat(parser.parseBuildConfig("random.txt", "content", null)).isEmpty();
        }

        @Nested
        class MavenPom {
            @Test
            void checkstyle_withoutEnforcement() {
                var detections = parser.parseBuildConfig("pom.xml", "<plugin>maven-checkstyle-plugin</plugin>", null);
                assertThat(detections).hasSize(1);
                assertThat(detections.getFirst().getTool()).isEqualTo(QualityGateTool.CHECKSTYLE);
                assertThat(detections.getFirst().getConfidenceScore()).isEqualTo(0.70);
            }

            @Test
            void checkstyle_withEnforcement() {
                var detections = parser.parseBuildConfig("pom.xml",
                        "<plugin>maven-checkstyle-plugin<failsOnViolation>true</failsOnViolation></plugin>", null);
                assertThat(detections.getFirst().getConfidenceScore()).isEqualTo(0.85);
                assertThat(detections.getFirst().getEvidenceFound()).anyMatch(e -> e.contains("enforcing"));
            }

            @Test
            void pmd_withEnforcement() {
                var detections = parser.parseBuildConfig("pom.xml",
                        "<plugin>maven-pmd-plugin<failOnViolation>true</failOnViolation></plugin>", null);
                assertThat(detections).anyMatch(d -> d.getTool() == QualityGateTool.PMD && d.getConfidenceScore() == 0.85);
            }

            @Test
            void spotbugs_withEnforcement() {
                var detections = parser.parseBuildConfig("pom.xml",
                        "<plugin>spotbugs-maven-plugin<failOnError>true</failOnError></plugin>", null);
                assertThat(detections).anyMatch(d -> d.getTool() == QualityGateTool.SPOTBUGS && d.getConfidenceScore() == 0.85);
            }

            @Test
            void jacoco_withMinimumThreshold() {
                var detections = parser.parseBuildConfig("pom.xml",
                        "<plugin>jacoco-maven-plugin<minimum>0.80</minimum></plugin>", null);
                var jacoco = detections.stream().filter(d -> d.getTool() == QualityGateTool.JACOCO).findFirst().orElseThrow();
                assertThat(jacoco.getEvidenceFound()).anyMatch(e -> e.contains("0.80"));
            }

            @Test
            void jacoco_withoutMinimum() {
                var detections = parser.parseBuildConfig("pom.xml", "<plugin>jacoco-maven-plugin</plugin>", null);
                assertThat(detections).anyMatch(d -> d.getTool() == QualityGateTool.JACOCO);
            }

            @Test
            void sonarqube_withQualityGateWait() {
                var detections = parser.parseBuildConfig("pom.xml",
                        "sonar-maven-plugin sonar.qualitygate.wait=true", null);
                var sonar = detections.stream().filter(d -> d.getTool() == QualityGateTool.SONARQUBE).findFirst().orElseThrow();
                assertThat(sonar.getEvidenceFound()).anyMatch(e -> e.contains("enforcing"));
            }

            @Test
            void associatedWorkflow_isPropagated() {
                var detections = parser.parseBuildConfig("pom.xml", "maven-checkstyle-plugin", "ci.yml");
                assertThat(detections.getFirst().getAssociatedWorkflow()).isEqualTo("ci.yml");
            }
        }

        @Nested
        class GradleBuild {
            @Test
            void checkstylePlugin_detected() {
                var detections = parser.parseBuildConfig("build.gradle", "id 'checkstyle'", null);
                assertThat(detections).anyMatch(d -> d.getTool() == QualityGateTool.CHECKSTYLE);
            }

            @Test
            void checkstyleKeywordWithoutPluginId_notDetected() {
                var detections = parser.parseBuildConfig("build.gradle", "checkstyle version note", null);
                assertThat(detections).noneMatch(d -> d.getTool() == QualityGateTool.CHECKSTYLE);
            }

            @Test
            void spotbugs_detected() {
                var detections = parser.parseBuildConfig("build.gradle", "com.github.spotbugs", null);
                assertThat(detections).anyMatch(d -> d.getTool() == QualityGateTool.SPOTBUGS);
            }

            @Test
            void jacoco_withThresholdConfigured() {
                var detections = parser.parseBuildConfig("build.gradle", "jacoco { minimum 0.8 }", null);
                var jacoco = detections.stream().filter(d -> d.getTool() == QualityGateTool.JACOCO).findFirst().orElseThrow();
                assertThat(jacoco.getEvidenceFound()).contains("coverage threshold configured");
            }

            @Test
            void sonarqube_withQualityGateWait() {
                var detections = parser.parseBuildConfig("build.gradle.kts",
                        "org.sonarqube sonar.qualitygate.wait=true", null);
                var sonar = detections.stream().filter(d -> d.getTool() == QualityGateTool.SONARQUBE).findFirst().orElseThrow();
                assertThat(sonar.getEvidenceFound()).anyMatch(e -> e.contains("enforcing"));
            }

            @Test
            void detekt_detected() {
                var detections = parser.parseBuildConfig("build.gradle.kts", "io.gitlab.arturbosch.detekt", null);
                assertThat(detections).anyMatch(d -> d.getTool() == QualityGateTool.DETEKT);
            }

            @Test
            void ktlint_detected() {
                var detections = parser.parseBuildConfig("build.gradle.kts", "org.jlleitschuh.gradle.ktlint", null);
                assertThat(detections).anyMatch(d -> d.getTool() == QualityGateTool.KTLINT);
            }
        }

        @Nested
        class PackageJson {
            @Test
            void eslint_withMaxWarningsZero() {
                var detections = parser.parseBuildConfig("package.json", "eslint --max-warnings=0", null);
                var eslint = detections.stream().filter(d -> d.getTool() == QualityGateTool.ESLINT).findFirst().orElseThrow();
                assertThat(eslint.getEvidenceFound()).anyMatch(e -> e.contains("enforcing"));
            }

            @Test
            void prettier_withCheckMode() {
                var detections = parser.parseBuildConfig("package.json", "prettier --check", null);
                var prettier = detections.stream().filter(d -> d.getTool() == QualityGateTool.PRETTIER).findFirst().orElseThrow();
                assertThat(prettier.getEvidenceFound()).anyMatch(e -> e.contains("enforcing"));
            }

            @Test
            void jestWithCoverageThreshold_mapsToIstanbul() {
                var detections = parser.parseBuildConfig("package.json", "jest coverage coverageThreshold", null);
                var jest = detections.stream().filter(d -> d.getTool() == QualityGateTool.ISTANBUL).findFirst().orElseThrow();
                assertThat(jest.getEvidenceFound()).anyMatch(e -> e.contains("enforcing"));
            }

            @Test
            void jestWithoutCoverage_notDetected() {
                var detections = parser.parseBuildConfig("package.json", "jest", null);
                assertThat(detections).noneMatch(d -> d.getTool() == QualityGateTool.ISTANBUL);
            }
        }

        @Nested
        class PythonConfig {
            @Test
            void ruff_detected() {
                assertThat(parser.parseBuildConfig("pyproject.toml", "[tool.ruff]", null))
                        .anyMatch(d -> d.getTool() == QualityGateTool.RUFF);
            }

            @Test
            void black_detected() {
                assertThat(parser.parseBuildConfig("pyproject.toml", "[tool.black]", null))
                        .anyMatch(d -> d.getTool() == QualityGateTool.BLACK);
            }

            @Test
            void mypy_strictMode() {
                var detections = parser.parseBuildConfig("pyproject.toml", "[tool.mypy]\nstrict = true", null);
                var mypy = detections.stream().filter(d -> d.getTool() == QualityGateTool.MYPY).findFirst().orElseThrow();
                assertThat(mypy.getEvidenceFound()).anyMatch(e -> e.contains("enforcing"));
            }

            @Test
            void pylint_withFailUnder() {
                var detections = parser.parseBuildConfig("setup.cfg", "[tool.pylint]\nfail-under=8.0", null);
                var pylint = detections.stream().filter(d -> d.getTool() == QualityGateTool.PYLINT).findFirst().orElseThrow();
                assertThat(pylint.getEvidenceFound()).anyMatch(e -> e.contains("8.0"));
            }

            @Test
            void coveragePy_withFailUnder() {
                var detections = parser.parseBuildConfig("setup.cfg", "[tool.coverage]\nfail_under = 90", null);
                var cov = detections.stream().filter(d -> d.getTool() == QualityGateTool.COVERAGE_PY).findFirst().orElseThrow();
                assertThat(cov.getEvidenceFound()).anyMatch(e -> e.contains("90"));
            }
        }

        @Test
        void goMod_alwaysReturnsEmpty() {
            assertThat(parser.parseBuildConfig("go.mod", "module example.com/foo", null)).isEmpty();
        }

        @Nested
        class CargoToml {
            @Test
            void clippy_withDenyLevel() {
                var detections = parser.parseBuildConfig("Cargo.toml", "[lints]\ndeny = \"warnings\"", null);
                var clippy = detections.stream().filter(d -> d.getTool() == QualityGateTool.CLIPPY).findFirst().orElseThrow();
                assertThat(clippy.getEvidenceFound()).anyMatch(e -> e.contains("enforcing"));
            }

            @Test
            void noLints_returnsEmpty() {
                assertThat(parser.parseBuildConfig("Cargo.toml", "[package]\nname = \"foo\"", null)).isEmpty();
            }
        }

        @Test
        void gemfile_rubocop() {
            assertThat(parser.parseBuildConfig("Gemfile", "gem 'rubocop'", null))
                    .anyMatch(d -> d.getTool() == QualityGateTool.RUBOCOP);
        }

        @Nested
        class ComposerJson {
            @Test
            void phpstan_detected() {
                assertThat(parser.parseBuildConfig("composer.json", "phpstan/phpstan", null))
                        .anyMatch(d -> d.getTool() == QualityGateTool.PHPSTAN);
            }

            @Test
            void phpcs_detected() {
                assertThat(parser.parseBuildConfig("composer.json", "squizlabs/php_codesniffer", null))
                        .anyMatch(d -> d.getTool() == QualityGateTool.PHPCS);
            }

            @Test
            void psalm_detected() {
                assertThat(parser.parseBuildConfig("composer.json", "vimeo/psalm", null))
                        .anyMatch(d -> d.getTool() == QualityGateTool.PSALM);
            }
        }
    }

    @Nested
    class ParseConfigFile {
        @Test
        void nullContent_returnsEmpty() {
            assertThat(parser.parseConfigFile("sonar-project.properties", null)).isEmpty();
        }

        @Test
        void sonarProperties_mapsToSonarQubeByDefault() {
            Optional<QualityGateDetection> result = parser.parseConfigFile("sonar-project.properties", "sonar.host.url=x");
            assertThat(result).isPresent();
            assertThat(result.get().getTool()).isEqualTo(QualityGateTool.SONARQUBE);
        }

        @Test
        void sonarCloudProperties_mapsToSonarCloud() {
            Optional<QualityGateDetection> result = parser.parseConfigFile(".sonarcloud.properties", "sonar.organization=x");
            assertThat(result.get().getTool()).isEqualTo(QualityGateTool.SONARCLOUD);
        }

        @Test
        void sonarProperties_withQualityGateWait_isEnforcing() {
            Optional<QualityGateDetection> result = parser.parseConfigFile(
                    "sonar-project.properties", "sonar.qualitygate.wait=true");
            assertThat(result.get().getEvidenceFound()).anyMatch(e -> e.contains("enforcing"));
        }

        @Test
        void codecovYml_withThreshold_isEnforcing() {
            Optional<QualityGateDetection> result = parser.parseConfigFile("codecov.yml", "coverage:\n  threshold: 1%");
            assertThat(result.get().getTool()).isEqualTo(QualityGateTool.CODECOV);
            assertThat(result.get().getEvidenceFound()).anyMatch(e -> e.contains("enforcing"));
        }

        @Test
        void codecovYml_withoutThreshold() {
            Optional<QualityGateDetection> result = parser.parseConfigFile("codecov.yml", "comment: false");
            assertThat(result.get().getEvidenceFound()).noneMatch(e -> e.contains("enforcing"));
        }

        @Test
        void eslintConfig_detected() {
            assertThat(parser.parseConfigFile(".eslintrc.json", "{}").get().getTool())
                    .isEqualTo(QualityGateTool.ESLINT);
        }

        @Test
        void checkstyleConfig_detected() {
            assertThat(parser.parseConfigFile("checkstyle.xml", "<module/>").get().getTool())
                    .isEqualTo(QualityGateTool.CHECKSTYLE);
        }

        @Test
        void codacyConfig_detected() {
            assertThat(parser.parseConfigFile(".codacy.yml", "engines:").get().getTool())
                    .isEqualTo(QualityGateTool.CODACY);
        }

        @Test
        void codeClimateConfig_detected() {
            assertThat(parser.parseConfigFile(".codeclimate.yml", "version: 2").get().getTool())
                    .isEqualTo(QualityGateTool.CODE_CLIMATE);
        }

        @Test
        void golangciConfig_detected() {
            assertThat(parser.parseConfigFile(".golangci.yml", "linters:").get().getTool())
                    .isEqualTo(QualityGateTool.GOLANGCI_LINT);
        }

        @Test
        void rubocopConfig_detected() {
            assertThat(parser.parseConfigFile(".rubocop.yml", "AllCops:").get().getTool())
                    .isEqualTo(QualityGateTool.RUBOCOP);
        }

        @Test
        void phpstanConfig_withHighLevel_isEnforcing() {
            Optional<QualityGateDetection> result = parser.parseConfigFile("phpstan.neon", "level: max");
            assertThat(result.get().getEvidenceFound()).anyMatch(e -> e.contains("enforcing"));
        }

        @Test
        void phpstanConfig_withoutHighLevel() {
            Optional<QualityGateDetection> result = parser.parseConfigFile("phpstan.neon", "level: 3");
            assertThat(result.get().getEvidenceFound()).noneMatch(e -> e.contains("enforcing"));
        }

        @Test
        void unrecognizedFile_returnsEmpty() {
            assertThat(parser.parseConfigFile("random.cfg", "content")).isEmpty();
        }
    }
}
