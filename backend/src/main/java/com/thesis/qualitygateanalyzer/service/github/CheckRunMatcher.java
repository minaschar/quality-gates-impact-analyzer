package com.thesis.qualitygateanalyzer.service.github;

import com.thesis.qualitygateanalyzer.domain.qualitygate.CheckRun;
import com.thesis.qualitygateanalyzer.domain.qualitygate.CommitStatus;

import java.util.List;
import java.util.Map;

/**
 * Matches GitHub check runs and commit statuses to quality gate tools.
 * <p>
 * Handles three matching scenarios:
 * 1. Direct quality gate app contexts (SonarCloud, Codecov, Codacy, etc.)
 * 2. CI/CD providers wrapping quality gate tools (CodeBuild, CircleCI, Jenkins, etc.)
 * 3. Generic patterns via the QualityGateTool enum
 */
public interface CheckRunMatcher {

    /**
     * Parse and match a raw check run to a QG tool.
     */
    CheckRun parseAndMatch(Map<String, Object> raw);

    /**
     * Parse and match a raw commit status to a quality gate tool.
     * Commit statuses are posted by external GitHub Apps via the Statuses API.
     */
    CommitStatus parseAndMatchStatus(Map<String, Object> raw);

    /**
     * Filter list of check runs to only QG-related ones.
     */
    List<CheckRun> filterQualityGateCheckRuns(List<CheckRun> checkRuns);

    /**
     * Filter to thesis-relevant QG check runs only.
     */
    List<CheckRun> filterThesisRelevant(List<CheckRun> checkRuns);

    /**
     * Get failed QG check runs.
     */
    List<CheckRun> getFailedQGCheckRuns(List<CheckRun> checkRuns);

    /**
     * Filter commit statuses to only quality gate related ones.
     */
    List<CommitStatus> filterQualityGateStatuses(List<CommitStatus> statuses);

    /**
     * Get failed quality gate commit statuses.
     */
    List<CommitStatus> getFailedQGStatuses(List<CommitStatus> statuses);
}
