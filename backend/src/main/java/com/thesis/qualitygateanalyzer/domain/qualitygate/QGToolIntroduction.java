package com.thesis.qualitygateanalyzer.domain.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateCategory;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * Complete introduction history for a single quality gate tool.
 */
@Value
@Builder
public class QGToolIntroduction {
    QualityGateTool tool;
    QualityGateCategory category;

    // Config introduction (build files, config files)
    List<QGFileIntroduction> configIntroductions;

    // CI/CD activation (workflow files)
    List<QGFileIntroduction> ciIntroductions;

    // Computed effective introduction
    CommitInfo effectiveIntroductionCommit;     // Earliest commit overall
    Instant effectiveDate;                       // Earliest date
    boolean presentSinceRepoCreation;            // True if tool existed from first commit

    // Summary
    String introductionSummary;                  // Human-readable summary
}
