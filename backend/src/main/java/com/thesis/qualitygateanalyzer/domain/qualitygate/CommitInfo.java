package com.thesis.qualitygateanalyzer.domain.qualitygate;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Information about a specific commit.
 */
@Value
@Builder
public class CommitInfo {
    String sha;
    String shortSha;
    String author;
    Instant date;
    String message;
}
