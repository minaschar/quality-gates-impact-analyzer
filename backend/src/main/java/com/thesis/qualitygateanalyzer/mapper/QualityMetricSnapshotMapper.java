package com.thesis.qualitygateanalyzer.mapper;

import com.thesis.qualitygateanalyzer.dto.response.QualityMetricSnapshotDto;
import com.thesis.qualitygateanalyzer.entity.qualitymetrics.QualityMetricSnapshotEntity;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for QualityMetricSnapshotEntity to QualityMetricSnapshotDto.
 * Every field name matches between entity and DTO, aside from the owner/repo/repositoryUrl
 * fields (which the entity carries but the DTO intentionally omits, since the response
 * wrapper already carries owner/repo at the top level).
 */
@Mapper(componentModel = "spring")
public interface QualityMetricSnapshotMapper {

    QualityMetricSnapshotDto toDto(QualityMetricSnapshotEntity entity);
}
