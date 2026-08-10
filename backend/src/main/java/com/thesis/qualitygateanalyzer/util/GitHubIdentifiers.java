package com.thesis.qualitygateanalyzer.util;

import java.util.Locale;

/**
 * GitHub organization/repository names are case-insensitive (github.com/Apache/spark and
 * github.com/apache/SPARK resolve to the same repository), but every {@code owner}/{@code repo}
 * column in this app's own tables is matched with a plain, case-sensitive {@code =}. Without a
 * single canonical form, the same real-world repository requested with different casing across
 * calls resolves to different DB rows -- cache hits are missed, {@code forceNewAnalysis} deletes
 * fail to find the previously stored rows to override, and duplicate/orphaned data accumulates.
 * <p>
 * Call {@link #normalize(String)} on every owner/organization and repo value as the first thing
 * that happens at each entry point (controller method, and any service method reachable
 * independently of a controller) that receives one from a caller, so every persistence lookup,
 * write, and outbound GitHub API call downstream operates on one canonical (trimmed, lower-case)
 * form.
 */
public final class GitHubIdentifiers {

    private GitHubIdentifiers() {
    }

    public static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
