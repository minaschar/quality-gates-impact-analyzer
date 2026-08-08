package com.thesis.qualitygateanalyzer.domain.qualitygate;

import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Complete repository detection result.
 */
@Value
@Builder
public class RepositoryDetectionResult {
    // Repository info
    String owner;
    String repo;
    String url;
    String description;
    String primaryLanguage;
    String defaultBranch;
    int stars;
    int forks;

    // Phase 1: Configuration detection
    List<QualityGateDetection> allDetections;
    List<QualityGateDetection> thesisRelevantDetections;
    List<QualityGateWorkflow> qualityGateWorkflows;
    boolean hasQualityGate;

    // Phase 1.5: Branch protection
    BranchProtection branchProtection;
    Set<QualityGateTool> requiredQGTools;      // QGs that are required checks
    Set<QualityGateTool> informationalQGTools; // QGs detected but NOT required

    // Phase 2-4: Enforcement detection
    EnforcementDetectionResult enforcement;

    // Summary
    String conclusion;
    String recommendation;

    // Metadata
    long detectionTimeMs;
    Instant detectedAt;
    int apiCallsMade;

    QualityGateHistoryDetection qualityGateHistory;
}
