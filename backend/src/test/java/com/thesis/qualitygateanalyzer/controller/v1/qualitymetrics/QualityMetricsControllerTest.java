package com.thesis.qualitygateanalyzer.controller.v1.qualitymetrics;

import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.dto.response.BulkIngestSummary;
import com.thesis.qualitygateanalyzer.dto.response.QualityMetricsResponse;
import com.thesis.qualitygateanalyzer.exception.NoDataInDatasetException;
import com.thesis.qualitygateanalyzer.exception.QualityMetricsNotFoundException;
import com.thesis.qualitygateanalyzer.service.qualitymetrics.QualityMetricsIngestionService;
import com.thesis.qualitygateanalyzer.service.qualitymetrics.QualityMetricsIngestionService.IngestResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityMetricsControllerTest {

    @Mock
    private QualityMetricsIngestionService ingestionService;

    private QualityMetricsController controller;

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";

    @BeforeEach
    void setUp() {
        controller = new QualityMetricsController(ingestionService);
    }

    @Nested
    class Ingest {
        @Test
        void fromCache_returnsCacheMessage() {
            QualityMetricsResponse resp = QualityMetricsResponse.builder().organization(OWNER).repo(REPO).build();
            when(ingestionService.ingest(OWNER, REPO, false)).thenReturn(new IngestResult(resp, true));

            ResponseEntity<ApiResponse<QualityMetricsResponse>> response = controller.ingest(OWNER, REPO, false);

            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getMessage()).isEqualTo("Quality metrics retrieved from cache");
        }

        @Test
        void fresh_returnsIngestedMessage() {
            QualityMetricsResponse resp = QualityMetricsResponse.builder().organization(OWNER).repo(REPO).build();
            when(ingestionService.ingest(OWNER, REPO, true)).thenReturn(new IngestResult(resp, false));

            ResponseEntity<ApiResponse<QualityMetricsResponse>> response = controller.ingest(OWNER, REPO, true);

            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getMessage()).isEqualTo("Quality metrics ingested and saved");
        }

        @Test
        void ownerAndRepoCasing_isNormalizedBeforeCallingService() {
            QualityMetricsResponse resp = QualityMetricsResponse.builder().organization(OWNER).repo(REPO).build();
            when(ingestionService.ingest(OWNER, REPO, false)).thenReturn(new IngestResult(resp, true));

            controller.ingest(" Octocat ", "Hello-World", false);

            verify(ingestionService).ingest(OWNER, REPO, false);
        }

        @Test
        void illegalArgument_returnsBadRequest() {
            when(ingestionService.ingest(OWNER, REPO, false)).thenThrow(new IllegalArgumentException("bad input"));
            ResponseEntity<ApiResponse<QualityMetricsResponse>> response = controller.ingest(OWNER, REPO, false);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void noDataInDataset_isRethrown() {
            when(ingestionService.ingest(OWNER, REPO, false)).thenThrow(new NoDataInDatasetException("no rows"));
            assertThrows(NoDataInDatasetException.class, () -> controller.ingest(OWNER, REPO, false));
        }

        @Test
        void unexpectedException_returns500() {
            when(ingestionService.ingest(OWNER, REPO, false)).thenThrow(new RuntimeException("boom"));
            ResponseEntity<ApiResponse<QualityMetricsResponse>> response = controller.ingest(OWNER, REPO, false);
            assertThat(response.getStatusCode().value()).isEqualTo(500);
        }
    }

    @Nested
    class BulkIngest {
        @Test
        void success_buildsSummaryMessage() {
            BulkIngestSummary summary = BulkIngestSummary.builder()
                    .totalRepositoriesInDataset(10).repositoriesIngested(7).repositoriesFromCache(3)
                    .totalRecordsIngested(500).build();
            when(ingestionService.bulkIngest(false)).thenReturn(summary);

            ResponseEntity<ApiResponse<BulkIngestSummary>> response = controller.bulkIngest(false);

            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getMessage()).contains("7 of 10").contains("3 served from cache");
        }

        @Test
        void unexpectedException_returns500() {
            when(ingestionService.bulkIngest(false)).thenThrow(new RuntimeException("boom"));
            ResponseEntity<ApiResponse<BulkIngestSummary>> response = controller.bulkIngest(false);
            assertThat(response.getStatusCode().value()).isEqualTo(500);
        }
    }

    @Nested
    class GetStored {
        @Test
        void present_returnsOk() {
            when(ingestionService.hasStoredMetrics(OWNER, REPO)).thenReturn(true);
            QualityMetricsResponse resp = QualityMetricsResponse.builder().organization(OWNER).repo(REPO).build();
            when(ingestionService.getStored(OWNER, REPO)).thenReturn(resp);

            ResponseEntity<ApiResponse<QualityMetricsResponse>> response = controller.getStored(OWNER, REPO);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test
        void absent_throwsQualityMetricsNotFoundException() {
            when(ingestionService.hasStoredMetrics(OWNER, REPO)).thenReturn(false);
            assertThrows(QualityMetricsNotFoundException.class, () -> controller.getStored(OWNER, REPO));
        }

        @Test
        void ownerAndRepoCasing_isNormalizedBeforeCallingService() {
            when(ingestionService.hasStoredMetrics(OWNER, REPO)).thenReturn(true);
            QualityMetricsResponse resp = QualityMetricsResponse.builder().organization(OWNER).repo(REPO).build();
            when(ingestionService.getStored(OWNER, REPO)).thenReturn(resp);

            controller.getStored(" Octocat ", "Hello-World");

            verify(ingestionService).hasStoredMetrics(OWNER, REPO);
            verify(ingestionService).getStored(OWNER, REPO);
        }
    }
}
