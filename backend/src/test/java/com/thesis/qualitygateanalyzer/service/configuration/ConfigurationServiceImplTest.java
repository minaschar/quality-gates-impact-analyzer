package com.thesis.qualitygateanalyzer.service.configuration;

import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity.ConfigCategory;
import com.thesis.qualitygateanalyzer.entity.configuration.ConfigurationEntity.DataType;
import com.thesis.qualitygateanalyzer.repository.configuration.ConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceImplTest {

    @Mock
    private ConfigurationRepository repository;

    private ConfigurationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConfigurationServiceImpl(repository);
    }

    private ConfigurationEntity entity(String key, String value, DataType type) {
        return ConfigurationEntity.builder()
                .configKey(key).configValue(value).dataType(type)
                .category(ConfigCategory.LIMITS).build();
    }

    @Nested
    class RefreshCacheAndInit {
        @Test
        void init_triggersRefreshCache() {
            when(repository.findAll()).thenReturn(List.of());
            service.init();
            verify(repository).findAll();
        }

        @Test
        void refreshCache_populatesFromAllDataTypes() {
            when(repository.findAll()).thenReturn(List.of(
                    entity("STR_KEY", "hello", DataType.STRING),
                    entity("INT_KEY", "42", DataType.INTEGER),
                    entity("BOOL_KEY", "true", DataType.BOOLEAN),
                    entity("DOUBLE_KEY", "3.14", DataType.DOUBLE)
            ));

            service.refreshCache();

            assertThat(service.getString("STR_KEY", "default")).isEqualTo("hello");
            assertThat(service.getInt("INT_KEY", -1)).isEqualTo(42);
            assertThat(service.getBoolean("BOOL_KEY", false)).isTrue();
            assertThat(service.getDouble("DOUBLE_KEY", -1.0)).isEqualTo(3.14);
        }

        @Test
        void refreshCache_skipsEntriesWithNullConfigValue() {
            ConfigurationEntity nullValueEntity = entity("NULL_KEY", null, DataType.STRING);
            when(repository.findAll()).thenReturn(List.of(nullValueEntity));

            service.refreshCache();

            // not cached -> falls through to repository lookup
            when(repository.findByConfigKey("NULL_KEY")).thenReturn(Optional.empty());
            assertThat(service.getString("NULL_KEY", "fallback")).isEqualTo("fallback");
        }
    }

    @Nested
    class TypedGetters {
        @Test
        void getString_cacheMiss_fallsBackToRepository() {
            when(repository.findByConfigKey("KEY")).thenReturn(Optional.of(entity("KEY", "value", DataType.STRING)));
            assertThat(service.getString("KEY", "default")).isEqualTo("value");
        }

        @Test
        void getString_notFoundAnywhere_returnsDefault() {
            when(repository.findByConfigKey("KEY")).thenReturn(Optional.empty());
            assertThat(service.getString("KEY", "default")).isEqualTo("default");
        }

        @Test
        void getInt_cacheMiss_fallsBackToRepository() {
            when(repository.findByConfigKey("KEY")).thenReturn(Optional.of(entity("KEY", "7", DataType.INTEGER)));
            assertThat(service.getInt("KEY", -1)).isEqualTo(7);
        }

        @Test
        void getInt_notFound_returnsDefault() {
            when(repository.findByConfigKey("KEY")).thenReturn(Optional.empty());
            assertThat(service.getInt("KEY", -1)).isEqualTo(-1);
        }

        @Test
        void getBoolean_cacheMiss_fallsBackToRepository() {
            when(repository.findByConfigKey("KEY")).thenReturn(Optional.of(entity("KEY", "true", DataType.BOOLEAN)));
            assertThat(service.getBoolean("KEY", false)).isTrue();
        }

        @Test
        void getBoolean_notFound_returnsDefault() {
            when(repository.findByConfigKey("KEY")).thenReturn(Optional.empty());
            assertThat(service.getBoolean("KEY", true)).isTrue();
        }

        @Test
        void getDouble_cacheMiss_fallsBackToRepository() {
            when(repository.findByConfigKey("KEY")).thenReturn(Optional.of(entity("KEY", "2.5", DataType.DOUBLE)));
            assertThat(service.getDouble("KEY", -1.0)).isEqualTo(2.5);
        }

        @Test
        void getDouble_notFound_returnsDefault() {
            when(repository.findByConfigKey("KEY")).thenReturn(Optional.empty());
            assertThat(service.getDouble("KEY", -1.0)).isEqualTo(-1.0);
        }
    }

    @Nested
    class ConvenienceGetters {
        @Test
        void allConvenienceGetters_delegateWithCorrectDefaults() {
            when(repository.findByConfigKey(anyString())).thenReturn(Optional.empty());

            assertThat(service.getGitHubToken()).isEqualTo("");
            assertThat(service.getSamplePrsLimit()).isEqualTo(100);
            assertThat(service.getWorkflowRunsLimit()).isEqualTo(100);
            assertThat(service.getPrAnalysisLimit()).isEqualTo(100);
            assertThat(service.getCheckRunsPerCommit()).isEqualTo(100);
            assertThat(service.getMaxBinarySearchIterations()).isEqualTo(50);
            assertThat(service.getLinearSearchThreshold()).isEqualTo(10);
            assertThat(service.getSamplePrsToReturn()).isEqualTo(5);
            assertThat(service.isPrFallbackEnabled()).isTrue();
            assertThat(service.isHistoryAnalysisEnabled()).isTrue();
            assertThat(service.isExternalCheckDetectionEnabled()).isTrue();
        }
    }

    @Nested
    class CrudOperations {
        @Test
        void findAll_delegatesToOrderedQuery() {
            when(repository.findAllByOrderByCategoryAscConfigKeyAsc()).thenReturn(List.of());
            assertThat(service.findAll()).isEmpty();
        }

        @Test
        void findByCategory_delegates() {
            when(repository.findByCategory(ConfigCategory.API)).thenReturn(List.of());
            assertThat(service.findByCategory(ConfigCategory.API)).isEmpty();
        }

        @Test
        void findByKey_delegates() {
            when(repository.findByConfigKey("KEY")).thenReturn(Optional.of(entity("KEY", "v", DataType.STRING)));
            assertThat(service.findByKey("KEY")).isPresent();
        }

        @Test
        void updateValue_keyNotFound_throwsIllegalArgumentException() {
            when(repository.findByConfigKey("MISSING")).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> service.updateValue("MISSING", "x"));
        }

        @Test
        void updateValue_updatesEntityAndCache() {
            ConfigurationEntity existing = entity("KEY", "old", DataType.STRING);
            when(repository.findByConfigKey("KEY")).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConfigurationEntity result = service.updateValue("KEY", "new");

            assertThat(result.getConfigValue()).isEqualTo("new");
            assertThat(service.getString("KEY", "fallback")).isEqualTo("new");
        }

        @Test
        void create_alreadyExists_throwsIllegalArgumentException() {
            when(repository.existsByConfigKey("KEY")).thenReturn(true);
            assertThrows(IllegalArgumentException.class,
                    () -> service.create("KEY", "v", DataType.STRING, ConfigCategory.API, "desc"));
        }

        @Test
        void create_success_savesAndCaches() {
            when(repository.existsByConfigKey("NEW_KEY")).thenReturn(false);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConfigurationEntity result = service.create("NEW_KEY", "5", DataType.INTEGER, ConfigCategory.LIMITS, "desc");

            assertThat(result.getConfigKey()).isEqualTo("NEW_KEY");
            assertThat(service.getInt("NEW_KEY", -1)).isEqualTo(5);
        }

        @Test
        void delete_removesFromRepositoryAndCache() {
            service.delete("KEY");
            verify(repository).deleteByConfigKey("KEY");
        }
    }

    @Nested
    class Validation {
        @Test
        void create_nullOrBlankValue_skipsValidation() {
            when(repository.existsByConfigKey("KEY")).thenReturn(false);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            service.create("KEY", null, DataType.INTEGER, ConfigCategory.LIMITS, "d");
            service.create("KEY2", "  ", DataType.INTEGER, ConfigCategory.LIMITS, "d");
        }

        @Test
        void create_invalidInteger_throws() {
            when(repository.existsByConfigKey("KEY")).thenReturn(false);
            assertThrows(IllegalArgumentException.class,
                    () -> service.create("KEY", "not-a-number", DataType.INTEGER, ConfigCategory.LIMITS, "d"));
        }

        @Test
        void create_validInteger_succeeds() {
            when(repository.existsByConfigKey("KEY")).thenReturn(false);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            service.create("KEY", "123", DataType.INTEGER, ConfigCategory.LIMITS, "d");
        }

        @Test
        void create_invalidBoolean_throws() {
            when(repository.existsByConfigKey("KEY")).thenReturn(false);
            assertThrows(IllegalArgumentException.class,
                    () -> service.create("KEY", "not-a-bool", DataType.BOOLEAN, ConfigCategory.LIMITS, "d"));
        }

        @Test
        void create_validBoolean_succeeds() {
            when(repository.existsByConfigKey("KEY")).thenReturn(false);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            service.create("KEY", "TRUE", DataType.BOOLEAN, ConfigCategory.LIMITS, "d");
        }

        @Test
        void create_invalidDouble_throws() {
            when(repository.existsByConfigKey("KEY")).thenReturn(false);
            assertThrows(IllegalArgumentException.class,
                    () -> service.create("KEY", "not-a-double", DataType.DOUBLE, ConfigCategory.LIMITS, "d"));
        }

        @Test
        void create_validDouble_succeeds() {
            when(repository.existsByConfigKey("KEY")).thenReturn(false);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            service.create("KEY", "1.23", DataType.DOUBLE, ConfigCategory.LIMITS, "d");
        }

        @Test
        void create_stringType_alwaysValid() {
            when(repository.existsByConfigKey("KEY")).thenReturn(false);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            service.create("KEY", "anything goes", DataType.STRING, ConfigCategory.LIMITS, "d");
        }
    }
}
