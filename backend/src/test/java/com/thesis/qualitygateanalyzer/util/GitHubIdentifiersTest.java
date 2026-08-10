package com.thesis.qualitygateanalyzer.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubIdentifiersTest {

    @Test
    void lowercasesAndTrims() {
        assertThat(GitHubIdentifiers.normalize(" Apache ")).isEqualTo("apache");
        assertThat(GitHubIdentifiers.normalize("SPARK")).isEqualTo("spark");
    }

    @Test
    void alreadyNormalized_isUnchanged() {
        assertThat(GitHubIdentifiers.normalize("apache")).isEqualTo("apache");
    }

    @Test
    void nullValue_returnsNull() {
        assertThat(GitHubIdentifiers.normalize(null)).isNull();
    }
}
