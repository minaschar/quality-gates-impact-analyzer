package com.thesis.qualitygateanalyzer.controller.v1.e2eanalysis;

import com.thesis.qualitygateanalyzer.domain.enums.ImpactTrend;
import com.thesis.qualitygateanalyzer.domain.qualitygate.RepositoryDetectionResult;
import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.dto.response.ImpactAnalysisResponse;
import com.thesis.qualitygateanalyzer.exception.RepositoryNotFoundException;
import com.thesis.qualitygateanalyzer.service.impactanalysis.ImpactAnalysisService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class E2EAnalysisControllerTest {

    @Mock
    private ImpactAnalysisService impactAnalysisService;

    private E2EAnalysisController controller;

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";

    @BeforeEach
    void setUp() {
        controller = new E2EAnalysisController(impactAnalysisService);
    }

    private RepositoryDetectionResult detection(boolean hasQualityGate) {
        return RepositoryDetectionResult.builder().owner(OWNER).repo(REPO).hasQualityGate(hasQualityGate).build();
    }

    private ImpactAnalysisResponse analysisResponse(boolean hasQualityGate) {
        return ImpactAnalysisResponse.builder()
                .owner(OWNER).repo(REPO).hasQualityGate(hasQualityGate)
                .overallTrend(ImpactTrend.IMPROVED)
                .comparisons(List.of())
                .timeline(List.of())
                .build();
    }

    @Nested
    class RunE2EAnalysis {

        @Test
        void refreshesThenAnalyzes_inOrder() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO)).thenReturn(detection(true));
            when(impactAnalysisService.analyze(OWNER, REPO, true)).thenReturn(analysisResponse(true));

            controller.runE2EAnalysis(OWNER, REPO);

            InOrder inOrder = inOrder(impactAnalysisService);
            inOrder.verify(impactAnalysisService).refreshRepositoryData(OWNER, REPO);
            inOrder.verify(impactAnalysisService).analyze(OWNER, REPO, true);
        }

        @Test
        void hasQualityGate_returns200_withCompletedMessage() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO)).thenReturn(detection(true));
            when(impactAnalysisService.analyze(OWNER, REPO, true)).thenReturn(analysisResponse(true));

            ResponseEntity<ApiResponse<ImpactAnalysisResponse>> result = controller.runE2EAnalysis(OWNER, REPO);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().getMessage()).isEqualTo("E2E analysis complete for " + OWNER + "/" + REPO);
            assertThat(result.getBody().getData().isHasQualityGate()).isTrue();
        }

        @Test
        void noQualityGate_returns200_withDescriptiveMessage() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO)).thenReturn(detection(false));
            when(impactAnalysisService.analyze(OWNER, REPO, true)).thenReturn(analysisResponse(false));

            ResponseEntity<ApiResponse<ImpactAnalysisResponse>> result = controller.runE2EAnalysis(OWNER, REPO);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().getMessage())
                    .isEqualTo("E2E analysis complete; no quality gate detected for " + OWNER + "/" + REPO);
        }

        @Test
        void ownerAndRepoCasing_isNormalizedBeforeCallingService() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO)).thenReturn(detection(true));
            when(impactAnalysisService.analyze(OWNER, REPO, true)).thenReturn(analysisResponse(true));

            controller.runE2EAnalysis(" Octocat ", "Hello-World");

            verify(impactAnalysisService).refreshRepositoryData(OWNER, REPO);
            verify(impactAnalysisService).analyze(OWNER, REPO, true);
        }

        @Test
        void refreshFails_neverCallsAnalyze() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO))
                    .thenThrow(new RepositoryNotFoundException("not found"));

            assertThrows(RepositoryNotFoundException.class, () -> controller.runE2EAnalysis(OWNER, REPO));

            verify(impactAnalysisService, never()).analyze(any(), any(), eq(true));
        }

        @Test
        void invalidArgument_returnsBadRequest() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO))
                    .thenThrow(new IllegalArgumentException("Invalid GitHub URL"));

            ResponseEntity<ApiResponse<ImpactAnalysisResponse>> result = controller.runE2EAnalysis(OWNER, REPO);

            assertThat(result.getStatusCode().value()).isEqualTo(400);
            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().isSuccess()).isFalse();
        }

        @Test
        void repositoryNotFound_propagatesToGlobalHandler() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO))
                    .thenThrow(new RepositoryNotFoundException("not found"));

            assertThrows(RepositoryNotFoundException.class, () -> controller.runE2EAnalysis(OWNER, REPO));
        }

        @Test
        void unexpectedException_returns500() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO))
                    .thenThrow(new RuntimeException("boom"));

            ResponseEntity<ApiResponse<ImpactAnalysisResponse>> result = controller.runE2EAnalysis(OWNER, REPO);

            assertThat(result.getStatusCode().value()).isEqualTo(500);
            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().getError()).contains("boom");
        }

        @Test
        void analyzeFails_returns500() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO)).thenReturn(detection(true));
            when(impactAnalysisService.analyze(OWNER, REPO, true)).thenThrow(new RuntimeException("boom"));

            ResponseEntity<ApiResponse<ImpactAnalysisResponse>> result = controller.runE2EAnalysis(OWNER, REPO);

            assertThat(result.getStatusCode().value()).isEqualTo(500);
        }
    }
}
