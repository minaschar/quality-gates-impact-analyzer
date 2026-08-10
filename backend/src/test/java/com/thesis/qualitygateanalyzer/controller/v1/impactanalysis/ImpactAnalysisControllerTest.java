package com.thesis.qualitygateanalyzer.controller.v1.impactanalysis;

import com.thesis.qualitygateanalyzer.domain.enums.ImpactTrend;
import com.thesis.qualitygateanalyzer.domain.qualitygate.RepositoryDetectionResult;
import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.dto.response.ImpactAnalysisResponse;
import com.thesis.qualitygateanalyzer.dto.response.ImpactAnalysisSummaryDto;
import com.thesis.qualitygateanalyzer.exception.ImpactAnalysisNotFoundException;
import com.thesis.qualitygateanalyzer.exception.RepositoryNotFoundException;
import com.thesis.qualitygateanalyzer.service.impactanalysis.ImpactAnalysisService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpactAnalysisControllerTest {

    @Mock
    private ImpactAnalysisService impactAnalysisService;

    private ImpactAnalysisController controller;

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";

    @BeforeEach
    void setUp() {
        controller = new ImpactAnalysisController(impactAnalysisService);
    }

    private ImpactAnalysisResponse response(boolean hasQualityGate, boolean cached) {
        return ImpactAnalysisResponse.builder()
                .owner(OWNER).repo(REPO).hasQualityGate(hasQualityGate).cached(cached)
                .overallTrend(ImpactTrend.IMPROVED)
                .comparisons(List.of())
                .timeline(List.of())
                .build();
    }

    @Nested
    class Analyze {

        @Test
        void noQualityGate_returns200_withDescriptiveMessage() {
            when(impactAnalysisService.analyze(OWNER, REPO, false)).thenReturn(response(false, false));

            ResponseEntity<ApiResponse<ImpactAnalysisResponse>> result = controller.analyze(OWNER, REPO, false);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().getMessage()).isEqualTo("No quality gate detected for " + OWNER + "/" + REPO);
            assertThat(result.getBody().getData().isHasQualityGate()).isFalse();
        }

        @Test
        void cachedResult_reportsCachedMessage() {
            when(impactAnalysisService.analyze(OWNER, REPO, false)).thenReturn(response(true, true));

            ResponseEntity<ApiResponse<ImpactAnalysisResponse>> result = controller.analyze(OWNER, REPO, false);

            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().getMessage()).isEqualTo("Impact analysis retrieved from cache");
        }

        @Test
        void freshResult_reportsCompletedMessage() {
            when(impactAnalysisService.analyze(OWNER, REPO, true)).thenReturn(response(true, false));

            ResponseEntity<ApiResponse<ImpactAnalysisResponse>> result = controller.analyze(OWNER, REPO, true);

            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().getMessage()).isEqualTo("Impact analysis completed");
        }

        @Test
        void ownerAndRepoCasing_isNormalizedBeforeCallingService() {
            when(impactAnalysisService.analyze(OWNER, REPO, false)).thenReturn(response(true, true));

            controller.analyze(" Octocat ", "Hello-World", false);

            verify(impactAnalysisService).analyze(OWNER, REPO, false);
        }

        @Test
        void invalidArgument_returnsBadRequest() {
            when(impactAnalysisService.analyze(OWNER, REPO, false))
                    .thenThrow(new IllegalArgumentException("bad owner"));

            ResponseEntity<ApiResponse<ImpactAnalysisResponse>> result = controller.analyze(OWNER, REPO, false);

            assertThat(result.getStatusCode().value()).isEqualTo(400);
            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().isSuccess()).isFalse();
        }

        @Test
        void repositoryNotFound_propagatesToGlobalHandler() {
            when(impactAnalysisService.analyze(OWNER, REPO, false))
                    .thenThrow(new RepositoryNotFoundException("not found"));

            assertThrows(RepositoryNotFoundException.class, () -> controller.analyze(OWNER, REPO, false));
        }

        @Test
        void unexpectedException_returns500() {
            when(impactAnalysisService.analyze(OWNER, REPO, false))
                    .thenThrow(new RuntimeException("boom"));

            ResponseEntity<ApiResponse<ImpactAnalysisResponse>> result = controller.analyze(OWNER, REPO, false);

            assertThat(result.getStatusCode().value()).isEqualTo(500);
        }
    }

    @Nested
    class RefreshData {

        private RepositoryDetectionResult detection(boolean hasQualityGate) {
            return RepositoryDetectionResult.builder().owner(OWNER).repo(REPO).hasQualityGate(hasQualityGate).build();
        }

        @Test
        void hasQualityGate_returns200_withRefreshedMessage() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO)).thenReturn(detection(true));

            ResponseEntity<ApiResponse<RepositoryDetectionResult>> result = controller.refreshData(OWNER, REPO);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().getMessage()).isEqualTo("Data refreshed for " + OWNER + "/" + REPO);
            assertThat(result.getBody().getData().isHasQualityGate()).isTrue();
        }

        @Test
        void noQualityGate_returns200_withDescriptiveMessage() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO)).thenReturn(detection(false));

            ResponseEntity<ApiResponse<RepositoryDetectionResult>> result = controller.refreshData(OWNER, REPO);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().getMessage())
                    .isEqualTo("Data refreshed; no quality gate detected for " + OWNER + "/" + REPO);
        }

        @Test
        void ownerAndRepoCasing_isNormalizedBeforeCallingService() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO)).thenReturn(detection(true));

            controller.refreshData(" Octocat ", "Hello-World");

            verify(impactAnalysisService).refreshRepositoryData(OWNER, REPO);
        }

        @Test
        void repositoryNotFound_propagatesToGlobalHandler() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO))
                    .thenThrow(new RepositoryNotFoundException("not found"));

            assertThrows(RepositoryNotFoundException.class, () -> controller.refreshData(OWNER, REPO));
        }

        @Test
        void invalidArgument_returnsBadRequest() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO))
                    .thenThrow(new IllegalArgumentException("Invalid GitHub URL"));

            ResponseEntity<ApiResponse<RepositoryDetectionResult>> result = controller.refreshData(OWNER, REPO);

            assertThat(result.getStatusCode().value()).isEqualTo(400);
            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().isSuccess()).isFalse();
        }

        @Test
        void unexpectedException_returns500() {
            when(impactAnalysisService.refreshRepositoryData(OWNER, REPO))
                    .thenThrow(new RuntimeException("boom"));

            ResponseEntity<ApiResponse<RepositoryDetectionResult>> result = controller.refreshData(OWNER, REPO);

            assertThat(result.getStatusCode().value()).isEqualTo(500);
            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().getError()).contains("boom");
        }
    }

    @Nested
    class GetImpactAnalysis {

        @Test
        void present_returnsIt() {
            when(impactAnalysisService.getAnalysis(OWNER, REPO)).thenReturn(response(true, true));

            ResponseEntity<ApiResponse<ImpactAnalysisResponse>> result = controller.getImpactAnalysis(OWNER, REPO);

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().getData().getOwner()).isEqualTo(OWNER);
        }

        @Test
        void absent_propagatesNotFoundToGlobalHandler() {
            when(impactAnalysisService.getAnalysis(OWNER, REPO))
                    .thenThrow(new ImpactAnalysisNotFoundException("not found"));

            assertThrows(ImpactAnalysisNotFoundException.class, () -> controller.getImpactAnalysis(OWNER, REPO));
        }

        @Test
        void ownerAndRepoCasing_isNormalizedBeforeCallingService() {
            when(impactAnalysisService.getAnalysis(OWNER, REPO)).thenReturn(response(true, true));

            controller.getImpactAnalysis(" Octocat ", "Hello-World");

            verify(impactAnalysisService).getAnalysis(OWNER, REPO);
        }
    }

    @Nested
    class ListImpactAnalyses {

        @Test
        void returnsSummaries() {
            ImpactAnalysisSummaryDto summary = ImpactAnalysisSummaryDto.builder().owner(OWNER).repo(REPO).build();
            when(impactAnalysisService.listAll()).thenReturn(List.of(summary));

            ResponseEntity<ApiResponse<List<ImpactAnalysisSummaryDto>>> result = controller.listImpactAnalyses();

            assertThat(result.getStatusCode().value()).isEqualTo(200);
            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().getData()).hasSize(1);
            assertThat(result.getBody().getMessage()).isEqualTo("Found 1 repositories");
        }

        @Test
        void empty_returnsEmptyList() {
            when(impactAnalysisService.listAll()).thenReturn(List.of());

            ResponseEntity<ApiResponse<List<ImpactAnalysisSummaryDto>>> result = controller.listImpactAnalyses();

            Assertions.assertNotNull(result.getBody());
            assertThat(result.getBody().getData()).isEmpty();
        }
    }
}
