package com.thesis.qualitygateanalyzer.exception;

/**
 * Represents an unrecoverable failure calling the GitHub API.
 * <p>
 * Not currently thrown anywhere: {@code GitHubApiClient} intentionally absorbs
 * transient/expected failures (404s, permission errors, timeouts) and returns
 * empty results so that detection can degrade gracefully rather than abort.
 * This type is available for call sites that need to surface a hard failure
 * instead of degrading.
 */
public class GitHubApiException extends QualityGateException {

    public GitHubApiException(String message) {
        super(message);
    }

    public GitHubApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
