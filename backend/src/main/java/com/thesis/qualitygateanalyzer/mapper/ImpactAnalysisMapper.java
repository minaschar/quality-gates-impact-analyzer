package com.thesis.qualitygateanalyzer.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.qualitygateanalyzer.dto.response.MetricComparisonDto;
import com.thesis.qualitygateanalyzer.dto.response.TimelinePointDto;
import com.thesis.qualitygateanalyzer.dto.response.ToolComparisonDto;
import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactComparisonEntity;
import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactMetricsSnapshotEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

/**
 * MapStruct mapper for impact-analysis Entity to DTO conversions.
 * <p>
 * Uses abstract class to allow injection of ObjectMapper for JSONB handling.
 */
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class ImpactAnalysisMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    public abstract TimelinePointDto toDto(ImpactMetricsSnapshotEntity entity);

    public abstract List<TimelinePointDto> toTimelineDtos(List<ImpactMetricsSnapshotEntity> entities);

    @Mapping(target = "samplesBefore", source = "samplesBefore", defaultValue = "0")
    @Mapping(target = "samplesAfter", source = "samplesAfter", defaultValue = "0")
    @Mapping(target = "metrics", source = "metricsComparison", qualifiedByName = "jsonToMetricsMap")
    public abstract ToolComparisonDto toDto(ImpactComparisonEntity entity);

    public abstract List<ToolComparisonDto> toComparisonDtos(List<ImpactComparisonEntity> entities);

    @SuppressWarnings("unchecked")
    @Named("jsonToMetricsMap")
    protected Map<String, MetricComparisonDto> jsonToMetricsMap(Object json) {
        if (json == null) return Map.of();
        try {
            if (json instanceof Map<?, ?> alreadyMap
                    && alreadyMap.values().stream().anyMatch(v -> v instanceof MetricComparisonDto)) {
                return (Map<String, MetricComparisonDto>) alreadyMap;
            }
            return objectMapper.convertValue(json, new TypeReference<Map<String, MetricComparisonDto>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
