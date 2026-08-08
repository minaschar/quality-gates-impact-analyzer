package com.thesis.qualitygateanalyzer.domain.enums;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EnumLogicTest {

    @Nested
    class QualityGateToolLogic {
        @Test
        void getThesisRelevant_includesOnlyRelevantCategories() {
            Set<QualityGateTool> relevant = QualityGateTool.getThesisRelevant();
            assertThat(relevant).contains(QualityGateTool.PMD, QualityGateTool.CHECKSTYLE, QualityGateTool.JACOCO);
            assertThat(relevant).doesNotContain(QualityGateTool.SNYK, QualityGateTool.DEPENDABOT);
        }

        @Test
        void getByCategory_returnsOnlyMatchingCategory() {
            Set<QualityGateTool> securityTools = QualityGateTool.getByCategory(QualityGateCategory.SECURITY);
            assertThat(securityTools).contains(QualityGateTool.SNYK, QualityGateTool.TRIVY);
            assertThat(securityTools).doesNotContain(QualityGateTool.PMD);
        }

        @Test
        void matchCheckRunName_nullInput_returnsEmpty() {
            assertThat(QualityGateTool.matchCheckRunName(null)).isEmpty();
        }

        @Test
        void matchCheckRunName_matchesKnownPattern() {
            Optional<QualityGateTool> result = QualityGateTool.matchCheckRunName("SonarCloud Code Analysis");
            assertThat(result).contains(QualityGateTool.SONARCLOUD);
        }

        @Test
        void matchCheckRunName_noMatch_returnsEmpty() {
            assertThat(QualityGateTool.matchCheckRunName("totally-unrelated-check")).isEmpty();
        }

        @Test
        void matchAppSlug_nullInput_returnsEmpty() {
            assertThat(QualityGateTool.matchAppSlug(null)).isEmpty();
        }

        @Test
        void matchAppSlug_matchesKnownSlug() {
            assertThat(QualityGateTool.matchAppSlug("sonarcloud")).contains(QualityGateTool.SONARCLOUD);
        }

        @Test
        void matchAppSlug_noMatch_returnsEmpty() {
            assertThat(QualityGateTool.matchAppSlug("unknown-app")).isEmpty();
        }

        @Test
        void isRelevantForThesis_delegatesToCategory() {
            assertThat(QualityGateTool.PMD.isRelevantForThesis()).isTrue();
            assertThat(QualityGateTool.SNYK.isRelevantForThesis()).isFalse();
        }
    }

    @Nested
    class EnforcementStatusLogic {
        @Test
        void fromScore_allThresholdBoundaries() {
            assertThat(EnforcementStatus.fromScore(1.0)).isEqualTo(EnforcementStatus.STRICTLY_ENFORCED);
            assertThat(EnforcementStatus.fromScore(0.95)).isEqualTo(EnforcementStatus.STRICTLY_ENFORCED);
            assertThat(EnforcementStatus.fromScore(0.94)).isEqualTo(EnforcementStatus.MOSTLY_ENFORCED);
            assertThat(EnforcementStatus.fromScore(0.80)).isEqualTo(EnforcementStatus.MOSTLY_ENFORCED);
            assertThat(EnforcementStatus.fromScore(0.79)).isEqualTo(EnforcementStatus.PARTIALLY_ENFORCED);
            assertThat(EnforcementStatus.fromScore(0.50)).isEqualTo(EnforcementStatus.PARTIALLY_ENFORCED);
            assertThat(EnforcementStatus.fromScore(0.49)).isEqualTo(EnforcementStatus.NOT_ENFORCED);
            assertThat(EnforcementStatus.fromScore(0.0)).isEqualTo(EnforcementStatus.NOT_ENFORCED);
        }

        @Test
        void isSuitableForStudy_trueOnlyWhenExplicitlyTrue() {
            assertThat(EnforcementStatus.STRICTLY_ENFORCED.isSuitableForStudy()).isTrue();
            assertThat(EnforcementStatus.NOT_ENFORCED.isSuitableForStudy()).isFalse();
            assertThat(EnforcementStatus.QG_ACTIVE_NO_FAILURES.isSuitableForStudy()).isFalse();
        }
    }

    @Nested
    class PROutcomeLogic {
        @Test
        void providesEnforcementEvidence_trueWhenNotNull() {
            assertThat(PROutcome.FIXED_THEN_MERGED.providesEnforcementEvidence()).isTrue();
            assertThat(PROutcome.BLOCKED.providesEnforcementEvidence()).isTrue();
            assertThat(PROutcome.MERGED_WITH_FAILURE.providesEnforcementEvidence()).isTrue();
        }

        @Test
        void providesEnforcementEvidence_falseWhenInconclusive() {
            assertThat(PROutcome.STILL_OPEN.providesEnforcementEvidence()).isFalse();
            assertThat(PROutcome.NO_FAILURE.providesEnforcementEvidence()).isFalse();
        }
    }

    @Nested
    class QualityGateCategoryLogic {
        @Test
        void relevantForThesisFlag_matchesExpectedCategories() {
            assertThat(QualityGateCategory.CODE_QUALITY.isRelevantForThesis()).isTrue();
            assertThat(QualityGateCategory.CODE_STYLE.isRelevantForThesis()).isTrue();
            assertThat(QualityGateCategory.COVERAGE.isRelevantForThesis()).isTrue();
            assertThat(QualityGateCategory.SECURITY.isRelevantForThesis()).isFalse();
            assertThat(QualityGateCategory.LICENSE.isRelevantForThesis()).isFalse();
        }
    }
}
