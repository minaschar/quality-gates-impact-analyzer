package com.thesis.qualitygateanalyzer.controller.v1.configuration;

import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity.ConfigCategory;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity.DataType;
import com.thesis.qualitygateanalyzer.service.configuration.ConfigurationService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationControllerTest {

    @Mock
    private ConfigurationService configurationService;

    private ConfigurationController controller;

    @BeforeEach
    void setUp() {
        controller = new ConfigurationController(configurationService);
    }

    private ConfigurationEntity entity(String key, String value) {
        return ConfigurationEntity.builder().configKey(key).configValue(value)
                .dataType(DataType.STRING).category(ConfigCategory.API).build();
    }

    @Nested
    class GetAll {
        @Test
        void masksTokenAndSecretValues() {
            ConfigurationEntity token = entity("GITHUB_TOKEN", "ghp_1234567890");
            ConfigurationEntity plain = entity("SAMPLE_PRS_LIMIT", "100");
            when(configurationService.findAll()).thenReturn(List.of(token, plain));

            ResponseEntity<ApiResponse<List<ConfigurationEntity>>> response = controller.getAll();

            assertThat(token.getConfigValue()).isEqualTo("***7890");
            assertThat(plain.getConfigValue()).isEqualTo("100");
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getMessage()).isEqualTo("Found 2 configuration entries");
        }

        @Test
        void shortTokenValue_isNotMasked() {
            ConfigurationEntity shortToken = entity("API_SECRET", "abc");
            when(configurationService.findAll()).thenReturn(List.of(shortToken));

            controller.getAll();

            assertThat(shortToken.getConfigValue()).isEqualTo("abc");
        }
    }

    @Nested
    class GetByCategory {
        @Test
        void validCategory_returnsFiltered() {
            when(configurationService.findByCategory(ConfigCategory.LIMITS)).thenReturn(List.of());
            ResponseEntity<ApiResponse<List<ConfigurationEntity>>> response = controller.getByCategory("limits");
            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test
        void invalidCategory_returnsBadRequest() {
            ResponseEntity<ApiResponse<List<ConfigurationEntity>>> response = controller.getByCategory("bogus");
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getError()).contains("Invalid category");
        }
    }

    @Nested
    class GetByKey {
        @Test
        void found_masksTokenKey() {
            when(configurationService.findByKey("GITHUB_TOKEN")).thenReturn(Optional.of(entity("GITHUB_TOKEN", "ghp_1234567890")));
            ResponseEntity<ApiResponse<ConfigurationEntity>> response = controller.getByKey("GITHUB_TOKEN");
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getData().getConfigValue()).isEqualTo("***7890");
        }

        @Test
        void found_nonSensitiveKey_isNotMasked() {
            when(configurationService.findByKey("SAMPLE_PRS_LIMIT")).thenReturn(Optional.of(entity("SAMPLE_PRS_LIMIT", "100")));
            ResponseEntity<ApiResponse<ConfigurationEntity>> response = controller.getByKey("SAMPLE_PRS_LIMIT");
            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getData().getConfigValue()).isEqualTo("100");
        }

        @Test
        void notFound_returns404() {
            when(configurationService.findByKey("MISSING")).thenReturn(Optional.empty());
            ResponseEntity<ApiResponse<ConfigurationEntity>> response = controller.getByKey("MISSING");
            assertThat(response.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Nested
    class UpdateValue {
        @Test
        void missingValueField_returnsBadRequest() {
            ResponseEntity<ApiResponse<ConfigurationEntity>> response = controller.updateValue("KEY", Map.of());
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void success_updatesAndMasksToken() {
            when(configurationService.updateValue("GITHUB_TOKEN", "ghp_newtoken1234"))
                    .thenReturn(entity("GITHUB_TOKEN", "ghp_newtoken1234"));

            ResponseEntity<ApiResponse<ConfigurationEntity>> response =
                    controller.updateValue("GITHUB_TOKEN", Map.of("value", "ghp_newtoken1234"));

            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getData().getConfigValue()).isEqualTo("***1234");
        }

        @Test
        void success_nonSensitiveKey_isNotMasked() {
            when(configurationService.updateValue("SAMPLE_PRS_LIMIT", "50"))
                    .thenReturn(entity("SAMPLE_PRS_LIMIT", "50"));

            ResponseEntity<ApiResponse<ConfigurationEntity>> response =
                    controller.updateValue("SAMPLE_PRS_LIMIT", Map.of("value", "50"));

            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().getData().getConfigValue()).isEqualTo("50");
        }

        @Test
        void invalidValue_returnsBadRequest() {
            when(configurationService.updateValue("SAMPLE_PRS_LIMIT", "not-a-number"))
                    .thenThrow(new IllegalArgumentException("Invalid value for type INTEGER: not-a-number"));

            ResponseEntity<ApiResponse<ConfigurationEntity>> response =
                    controller.updateValue("SAMPLE_PRS_LIMIT", Map.of("value", "not-a-number"));

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    class BatchUpdate {
        @Test
        void allSucceed_returnsSuccessTrue() {
            when(configurationService.updateValue(eq("KEY1"), any())).thenReturn(entity("KEY1", "v1"));
            when(configurationService.updateValue(eq("KEY2"), any())).thenReturn(entity("KEY2", "v2"));

            ResponseEntity<ApiResponse<Map<String, String>>> response =
                    controller.batchUpdate(Map.of("KEY1", "v1", "KEY2", "v2"));

            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).containsEntry("KEY1", "OK").containsEntry("KEY2", "OK");
        }

        @Test
        void partialFailure_returnsSuccessFalse() {
            when(configurationService.updateValue(eq("KEY1"), any())).thenReturn(entity("KEY1", "v1"));
            when(configurationService.updateValue(eq("KEY2"), any())).thenThrow(new IllegalArgumentException("bad value"));

            ResponseEntity<ApiResponse<Map<String, String>>> response =
                    controller.batchUpdate(Map.of("KEY1", "v1", "KEY2", "bad"));

            Assertions.assertNotNull(response.getBody());
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getData().get("KEY2")).contains("ERROR");
        }
    }

    @Nested
    class CacheAndConvenienceEndpoints {
        @Test
        void refreshCache_delegatesAndReturnsOk() {
            ResponseEntity<ApiResponse<Void>> response = controller.refreshCache();
            verify(configurationService).refreshCache();
            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test
        void getLimits_returnsAllLimitValues() {
            when(configurationService.getSamplePrsLimit()).thenReturn(100);
            when(configurationService.getWorkflowRunsLimit()).thenReturn(100);
            when(configurationService.getPrAnalysisLimit()).thenReturn(100);
            when(configurationService.getCheckRunsPerCommit()).thenReturn(100);
            when(configurationService.getMaxBinarySearchIterations()).thenReturn(50);
            when(configurationService.getLinearSearchThreshold()).thenReturn(10);
            when(configurationService.getSamplePrsToReturn()).thenReturn(5);

            ResponseEntity<Map<String, Integer>> response = controller.getLimits();

            assertThat(response.getBody()).containsEntry("SAMPLE_PRS_LIMIT", 100).containsEntry("SAMPLE_PRS_TO_RETURN", 5);
        }

        @Test
        void getFeatures_returnsAllFlags() {
            when(configurationService.isPrFallbackEnabled()).thenReturn(true);
            when(configurationService.isHistoryAnalysisEnabled()).thenReturn(false);
            when(configurationService.isExternalCheckDetectionEnabled()).thenReturn(true);

            ResponseEntity<Map<String, Boolean>> response = controller.getFeatures();

            assertThat(response.getBody()).containsEntry("ENABLE_PR_FALLBACK", true)
                    .containsEntry("ENABLE_HISTORY_ANALYSIS", false);
        }
    }
}
