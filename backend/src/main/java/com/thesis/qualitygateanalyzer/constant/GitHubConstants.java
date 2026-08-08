package com.thesis.qualitygateanalyzer.constant;

import java.time.Duration;

/**
 * Configuration values for the GitHub REST/GraphQL client.
 */
public final class GitHubConstants {

    private GitHubConstants() {
    }

    public static final String BASE_URL = "https://api.github.com";
    public static final String GRAPHQL_URL = "https://api.github.com/graphql";
    public static final Duration TIMEOUT = Duration.ofSeconds(30);
    public static final String ACCEPT_HEADER_VALUE = "application/vnd.github.v3+json";
    public static final String USER_AGENT = "QualityGateAnalyzer/1.0";
    public static final int MAX_PAGE_SIZE = 100;
}
