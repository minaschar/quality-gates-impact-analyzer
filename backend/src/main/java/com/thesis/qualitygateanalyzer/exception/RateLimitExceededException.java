package com.thesis.qualitygateanalyzer.exception;

/**
 * Represents the GitHub API rate limit being exhausted.
 * <p>
 * Not currently thrown anywhere: rate-limit responses are absorbed by
 * {@code GitHubApiClient} the same way other failures are (see
 * {@link GitHubApiException}). Available for callers that need to surface
 * this condition explicitly as HTTP 429.
 */
public class RateLimitExceededException extends GitHubApiException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
