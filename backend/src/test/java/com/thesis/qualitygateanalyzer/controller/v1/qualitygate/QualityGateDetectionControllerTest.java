package com.thesis.qualitygateanalyzer.controller.v1.qualitygate;

import com.thesis.qualitygateanalyzer.domain.qualitygate.RepositoryDetectionResult;
import com.thesis.qualitygateanalyzer.dto.request.QualityGateDetectionRequest;
import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.exception.QualityGateDetectionNotFoundException;
import com.thesis.qualitygateanalyzer.exception.RepositoryNotFoundException;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import com.thesis.qualitygateanalyzer.service.qualitygate.PersistenceService;
import com.thesis.qualitygateanalyzer.service.qualitygate.QualityGateDetectionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QualityGateDetectionControllerTest {

    @Mock
    private QualityGateDetectionService detectionService;
    @Mock
    private GitHubApiClient githubClient;
    @Mock
    private PersistenceService persistenceService;

    private QualityGateDetectionController controller;

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";
    private static final String URL = "https://github.com/octocat/hello-world";

    @BeforeEach
    void setUp() {
        controller = new QualityGateDetectionController(detectionService, githubClient, persistenceService);
    }

    private RepositoryDetectionResult result() {
        return RepositoryDetectionResult.builder().owner(OWNER).repo(REPO).build();
    }

    @Nested
    class DetectQualityGate {
        @Test
        void cachedResult_returnsWithoutRunningFreshDetection() {
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(result()));
            QualityGateDetectionRequest request = new QualityGateDetectionRequest();
            request.setRepositoryUrl(URL);

            ResponseEntity<ApiResponse<RepositoryDetectionResult>> response =
                    controller.detectQualityGate(request, false);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getMessage()).isEqualTo("Detection retrieved from cache");
            verifyNoInteractions(detectionService);
        }

        @Test
        void forceNew_bypassesCacheEvenIfPresent() {
            when(detectionService.detect(URL)).thenReturn(result());
            QualityGateDetectionRequest request = new QualityGateDetectionRequest();
            request.setRepositoryUrl(URL);

            ResponseEntity<ApiResponse<RepositoryDetectionResult>> response =
                    controller.detectQualityGate(request, true);

            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getMessage()).isEqualTo("Detection completed and saved");
            verify(persistenceService, never()).loadDetection(any(), any());
            verify(persistenceService).saveDetection(any(), eq(true));
        }

        @Test
        void noCacheHit_runsFreshDetectionAndSaves() {
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.empty());
            when(detectionService.detect(URL)).thenReturn(result());
            QualityGateDetectionRequest request = new QualityGateDetectionRequest();
            request.setRepositoryUrl(URL);

            ResponseEntity<ApiResponse<RepositoryDetectionResult>> response =
                    controller.detectQualityGate(request, false);

            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getMessage()).isEqualTo("Detection completed and cached");
            verify(persistenceService).saveDetection(any(), eq(false));
        }

        @Test
        void urlCasing_isNormalizedBeforeCacheLookup() {
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(result()));
            QualityGateDetectionRequest request = new QualityGateDetectionRequest();
            request.setRepositoryUrl("https://github.com/Octocat/Hello-World");

            controller.detectQualityGate(request, false);

            verify(persistenceService).loadDetection(OWNER, REPO);
        }

        @Test
        void urlCasing_isNormalizedBeforeCallingDetectionService() {
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.empty());
            when(detectionService.detect(URL)).thenReturn(result());
            QualityGateDetectionRequest request = new QualityGateDetectionRequest();
            request.setRepositoryUrl("https://github.com/Octocat/Hello-World.git");

            controller.detectQualityGate(request, false);

            verify(detectionService).detect(URL);
        }

        @Test
        void invalidUrl_returnsBadRequest() {
            QualityGateDetectionRequest request = new QualityGateDetectionRequest();
            request.setRepositoryUrl("not-a-url");

            ResponseEntity<ApiResponse<RepositoryDetectionResult>> response =
                    controller.detectQualityGate(request, false);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        void blankUrl_returnsBadRequest() {
            QualityGateDetectionRequest request = new QualityGateDetectionRequest();
            request.setRepositoryUrl("");

            ResponseEntity<ApiResponse<RepositoryDetectionResult>> response =
                    controller.detectQualityGate(request, false);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void repositoryNotFound_isRethrownForGlobalHandler() {
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.empty());
            when(detectionService.detect(URL)).thenThrow(new RepositoryNotFoundException("Repository not found: " + OWNER + "/" + REPO));
            QualityGateDetectionRequest request = new QualityGateDetectionRequest();
            request.setRepositoryUrl(URL);

            assertThrows(RepositoryNotFoundException.class, () -> controller.detectQualityGate(request, false));
        }

        @Test
        void unexpectedException_returns500() {
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.empty());
            when(detectionService.detect(URL)).thenThrow(new RuntimeException("boom"));
            QualityGateDetectionRequest request = new QualityGateDetectionRequest();
            request.setRepositoryUrl(URL);

            ResponseEntity<ApiResponse<RepositoryDetectionResult>> response =
                    controller.detectQualityGate(request, false);

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getError()).contains("boom");
        }
    }

    @Nested
    class GetQualityGateDetection {
        @Test
        void present_returnsOk() {
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(result()));
            ResponseEntity<ApiResponse<RepositoryDetectionResult>> response = controller.getQualityGateDetection(OWNER, REPO);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getData().getOwner()).isEqualTo(OWNER);
        }

        @Test
        void absent_throwsQualityGateDetectionNotFoundException() {
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.empty());
            assertThrows(QualityGateDetectionNotFoundException.class, () -> controller.getQualityGateDetection(OWNER, REPO));
        }

        @Test
        void ownerAndRepoCasing_isNormalizedBeforeCallingService() {
            when(persistenceService.loadDetection(OWNER, REPO)).thenReturn(Optional.of(result()));
            controller.getQualityGateDetection(" Octocat ", "Hello-World");
            verify(persistenceService).loadDetection(OWNER, REPO);
        }
    }

    @Nested
    class ListRepositories {
        @Test
        void noFilter_listsAll() {
            when(persistenceService.listAllDetections()).thenReturn(List.of(result()));
            ResponseEntity<ApiResponse<List<RepositoryDetectionResult>>> response = controller.listRepositories(null);
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getData()).hasSize(1);
            verify(persistenceService, never()).listWithQualityGate();
        }

        @Test
        void withQualityGateFilter_listsFiltered() {
            when(persistenceService.listWithQualityGate()).thenReturn(List.of(result()));
            ResponseEntity<ApiResponse<List<RepositoryDetectionResult>>> response = controller.listRepositories(true);
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getData()).hasSize(1);
            verify(persistenceService, never()).listAllDetections();
        }

        @Test
        void falseFilter_stillListsAll() {
            when(persistenceService.listAllDetections()).thenReturn(List.of());
            controller.listRepositories(false);
            verify(persistenceService).listAllDetections();
        }
    }

    @Nested
    class DeleteQualityGateDetection {
        @Test
        void present_deletesAndReturnsOk() {
            when(persistenceService.hasDetection(OWNER, REPO)).thenReturn(true);
            ResponseEntity<ApiResponse<Void>> response = controller.deleteQualityGateDetection(OWNER, REPO);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(persistenceService).deleteDetection(OWNER, REPO);
        }

        @Test
        void absent_throwsQualityGateDetectionNotFoundException() {
            when(persistenceService.hasDetection(OWNER, REPO)).thenReturn(false);
            assertThrows(QualityGateDetectionNotFoundException.class, () -> controller.deleteQualityGateDetection(OWNER, REPO));
            verify(persistenceService, never()).deleteDetection(any(), any());
        }

        @Test
        void ownerAndRepoCasing_isNormalizedBeforeCallingService() {
            when(persistenceService.hasDetection(OWNER, REPO)).thenReturn(true);
            controller.deleteQualityGateDetection(" Octocat ", "Hello-World");
            verify(persistenceService).deleteDetection(OWNER, REPO);
        }
    }

    @Nested
    class Utilities {
        @Test
        void rateLimit_highRemaining_ok() {
            when(githubClient.getRemainingRateLimit()).thenReturn(4000);
            ResponseEntity<Map<String, Object>> response = controller.getRateLimit();
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().get("message")).isEqualTo("OK");
        }

        @Test
        void rateLimit_lowRemaining_warns() {
            when(githubClient.getRemainingRateLimit()).thenReturn(10);
            ResponseEntity<Map<String, Object>> response = controller.getRateLimit();
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().get("message")).isEqualTo("Running low!");
        }

        @Test
        void health_returnsUp() {
            ResponseEntity<Map<String, String>> response = controller.health();
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().get("status")).isEqualTo("UP");
        }
    }
}
