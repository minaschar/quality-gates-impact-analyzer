package com.thesis.qualitygateanalyzer.service.git;

import com.thesis.qualitygateanalyzer.domain.qualitygate.QGFileIntroduction;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;

/**
 * Fallback mechanism for locating quality gate tool introductions when the Git Blame
 * approach cannot find a configuration file.
 */
public interface PRBasedIntroductionFinder {

    /**
     * Find the earliest PR where a specific quality gate tool appeared in check runs.
     * Uses binary search for efficiency.
     *
     * @return the first PR info as a QGFileIntroduction, or null if not found
     */
    QGFileIntroduction findToolIntroduction(String owner, String repo, QualityGateTool tool);
}
