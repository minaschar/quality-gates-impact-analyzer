package com.thesis.qualitygateanalyzer.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Categories of quality gates.
 */
@Getter
@RequiredArgsConstructor
public enum QualityGateCategory {

    CODE_QUALITY("Code Quality", "Bugs, code smells, maintainability", true),
    CODE_STYLE("Code Style", "Linting, formatting, conventions", true),
    COVERAGE("Test Coverage", "Test coverage thresholds", true),
    SECURITY("Security", "Vulnerability scanning", false),
    LICENSE("License", "License compliance", false);

    private final String displayName;
    private final String description;
    private final boolean relevantForThesis;
}
