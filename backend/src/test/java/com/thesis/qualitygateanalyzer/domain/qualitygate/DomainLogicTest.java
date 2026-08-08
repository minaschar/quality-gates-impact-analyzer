package com.thesis.qualitygateanalyzer.domain.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.PROutcome;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateCategory;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the custom (non-Lombok-generated) logic methods on the domain model classes.
 */
class DomainLogicTest {

    @Nested
    class WorkflowRunLogic {
        @Test
        void isFailed_true_whenConclusionIsFailure() {
            assertThat(WorkflowRun.builder().conclusion("failure").build().isFailed()).isTrue();
            assertThat(WorkflowRun.builder().conclusion("FAILURE").build().isFailed()).isTrue();
            assertThat(WorkflowRun.builder().conclusion("success").build().isFailed()).isFalse();
        }

        @Test
        void isSuccess_true_whenConclusionIsSuccess() {
            assertThat(WorkflowRun.builder().conclusion("success").build().isSuccess()).isTrue();
            assertThat(WorkflowRun.builder().conclusion("failure").build().isSuccess()).isFalse();
        }

        @Test
        void isForPR_true_whenPrNumberPresent() {
            assertThat(WorkflowRun.builder().prNumber(5).build().isForPR()).isTrue();
        }

        @Test
        void isForPR_true_whenEventIsPullRequest() {
            assertThat(WorkflowRun.builder().event("pull_request").build().isForPR()).isTrue();
        }

        @Test
        void isForPR_false_whenNeitherPresent() {
            assertThat(WorkflowRun.builder().event("push").build().isForPR()).isFalse();
        }
    }

    @Nested
    class CheckRunLogic {
        @Test
        void isFailed_trueForFailureAndActionRequired() {
            assertThat(CheckRun.builder().conclusion("failure").build().isFailed()).isTrue();
            assertThat(CheckRun.builder().conclusion("action_required").build().isFailed()).isTrue();
            assertThat(CheckRun.builder().conclusion("success").build().isFailed()).isFalse();
        }

        @Test
        void isSuccess_trueOnlyForSuccess() {
            assertThat(CheckRun.builder().conclusion("success").build().isSuccess()).isTrue();
        }

        @Test
        void isQualityGateRelated_dependsOnMatchedTool() {
            assertThat(CheckRun.builder().matchedTool(QualityGateTool.PMD).build().isQualityGateRelated()).isTrue();
            assertThat(CheckRun.builder().build().isQualityGateRelated()).isFalse();
        }

        @Test
        void isThesisRelevant_dependsOnToolCategory() {
            assertThat(CheckRun.builder().matchedTool(QualityGateTool.PMD).build().isThesisRelevant()).isTrue();
            assertThat(CheckRun.builder().matchedTool(QualityGateTool.SNYK).build().isThesisRelevant()).isFalse();
            assertThat(CheckRun.builder().build().isThesisRelevant()).isFalse();
        }

        @Test
        void getFailureMessage_prefersOutputTitle() {
            CheckRun withBoth = CheckRun.builder().outputTitle("Title").outputSummary("Summary").build();
            assertThat(withBoth.getFailureMessage()).isEqualTo("Title");
        }

        @Test
        void getFailureMessage_fallsBackToSummary_whenTitleBlank() {
            CheckRun blankTitle = CheckRun.builder().outputTitle("  ").outputSummary("Summary").build();
            assertThat(blankTitle.getFailureMessage()).isEqualTo("Summary");
            CheckRun nullTitle = CheckRun.builder().outputSummary("Summary").build();
            assertThat(nullTitle.getFailureMessage()).isEqualTo("Summary");
        }
    }

    @Nested
    class CommitStatusLogic {
        @Test
        void isFailed_trueForFailureAndError() {
            assertThat(CommitStatus.builder().state("failure").build().isFailed()).isTrue();
            assertThat(CommitStatus.builder().state("error").build().isFailed()).isTrue();
            assertThat(CommitStatus.builder().state("success").build().isFailed()).isFalse();
        }

        @Test
        void isSuccessAndIsPending() {
            assertThat(CommitStatus.builder().state("success").build().isSuccess()).isTrue();
            assertThat(CommitStatus.builder().state("pending").build().isPending()).isTrue();
        }

        @Test
        void isQualityGateRelatedAndThesisRelevant() {
            CommitStatus matched = CommitStatus.builder().matchedTool(QualityGateTool.CODECOV).build();
            assertThat(matched.isQualityGateRelated()).isTrue();
            assertThat(matched.isThesisRelevant()).isTrue();
            assertThat(CommitStatus.builder().build().isQualityGateRelated()).isFalse();
        }
    }

    @Nested
    class QualityGateDetectionLogic {
        @Test
        void isRelevantForThesis_delegatesToToolCategory() {
            QualityGateDetection relevant = QualityGateDetection.builder()
                    .tool(QualityGateTool.PMD).category(QualityGateCategory.CODE_STYLE).build();
            QualityGateDetection notRelevant = QualityGateDetection.builder()
                    .tool(QualityGateTool.SNYK).category(QualityGateCategory.SECURITY).build();
            assertThat(relevant.isRelevantForThesis()).isTrue();
            assertThat(notRelevant.isRelevantForThesis()).isFalse();
        }
    }

    @Nested
    class BranchProtectionLogic {
        private BranchProtection.RequiredCheck check(QualityGateTool tool) {
            return BranchProtection.RequiredCheck.builder().context("ctx").matchedTool(tool).build();
        }

        @Test
        void isToolRequired_falseWhenNotRequiringStatusChecks() {
            BranchProtection bp = BranchProtection.builder().requiresStatusChecks(false)
                    .requiredChecks(List.of(check(QualityGateTool.PMD))).build();
            assertThat(bp.isToolRequired(QualityGateTool.PMD)).isFalse();
        }

        @Test
        void isToolRequired_falseWhenRequiredChecksNull() {
            BranchProtection bp = BranchProtection.builder().requiresStatusChecks(true).build();
            assertThat(bp.isToolRequired(QualityGateTool.PMD)).isFalse();
        }

        @Test
        void isToolRequired_trueWhenMatchFound() {
            BranchProtection bp = BranchProtection.builder().requiresStatusChecks(true)
                    .requiredChecks(List.of(check(QualityGateTool.PMD))).build();
            assertThat(bp.isToolRequired(QualityGateTool.PMD)).isTrue();
            assertThat(bp.isToolRequired(QualityGateTool.CHECKSTYLE)).isFalse();
        }

        @Test
        void isCheckRequired_falseWhenCheckNameIsNull() {
            BranchProtection bp = BranchProtection.builder().requiresStatusChecks(true)
                    .requiredChecks(List.of(check(QualityGateTool.PMD))).build();
            assertThat(bp.isCheckRequired(null)).isFalse();
        }

        @Test
        void isCheckRequired_matchesCaseInsensitiveSubstring() {
            BranchProtection.RequiredCheck namedCheck = BranchProtection.RequiredCheck.builder()
                    .context("SonarCloud Code Analysis").build();
            BranchProtection bp = BranchProtection.builder().requiresStatusChecks(true)
                    .requiredChecks(List.of(namedCheck)).build();
            assertThat(bp.isCheckRequired("sonarcloud")).isTrue();
            assertThat(bp.isCheckRequired("codecov")).isFalse();
        }

        @Test
        void getRequiredQGTools_emptyWhenNotRequiringChecks() {
            BranchProtection bp = BranchProtection.builder().requiresStatusChecks(false).build();
            assertThat(bp.getRequiredQGTools()).isEmpty();
        }

        @Test
        void getRequiredQGTools_filtersNullMatchedTools() {
            BranchProtection bp = BranchProtection.builder().requiresStatusChecks(true)
                    .requiredChecks(List.of(check(QualityGateTool.PMD), check(null))).build();
            assertThat(bp.getRequiredQGTools()).containsExactly(QualityGateTool.PMD);
        }

        @Test
        void hasRequiredQualityGate_trueWhenAnyToolPresent() {
            BranchProtection withTool = BranchProtection.builder().requiresStatusChecks(true)
                    .requiredChecks(List.of(check(QualityGateTool.PMD))).build();
            BranchProtection withoutTool = BranchProtection.builder().requiresStatusChecks(false).build();
            assertThat(withTool.hasRequiredQualityGate()).isTrue();
            assertThat(withoutTool.hasRequiredQualityGate()).isFalse();
        }
    }

    @Nested
    class PRDetectionResultLogic {
        @Test
        void providesEnforcementEvidence_trueOnlyWhenVerifiedAndOutcomeProvidesEvidence() {
            PRDetectionResult verified = PRDetectionResult.builder()
                    .hadVerifiedQGFailure(true).outcome(PROutcome.FIXED_THEN_MERGED).build();
            PRDetectionResult unverified = PRDetectionResult.builder()
                    .hadVerifiedQGFailure(false).outcome(PROutcome.FIXED_THEN_MERGED).build();
            PRDetectionResult inconclusive = PRDetectionResult.builder()
                    .hadVerifiedQGFailure(true).outcome(PROutcome.STILL_OPEN).build();

            assertThat(verified.providesEnforcementEvidence()).isTrue();
            assertThat(unverified.providesEnforcementEvidence()).isFalse();
            assertThat(inconclusive.providesEnforcementEvidence()).isFalse();
        }
    }

    @Nested
    class ToolStatsLogic {
        @Test
        void getEnforcementRate_nullWhenNoActivity() {
            EnforcementDetectionResult.ToolStats stats = EnforcementDetectionResult.ToolStats.builder()
                    .tool(QualityGateTool.PMD).enforced(0).bypassed(0).build();
            assertThat(stats.getEnforcementRate()).isNull();
        }

        @Test
        void getEnforcementRate_computedFromEnforcedAndBypassed() {
            EnforcementDetectionResult.ToolStats stats = EnforcementDetectionResult.ToolStats.builder()
                    .tool(QualityGateTool.PMD).enforced(3).bypassed(1).build();
            assertThat(stats.getEnforcementRate()).isEqualTo(0.75);
        }
    }
}
