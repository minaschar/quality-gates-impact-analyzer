package com.thesis.qualitygateanalyzer.controller.v1.github;

import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubControllerTest {

    @Mock
    private GitHubApiClient githubClient;

    private GitHubController controller;

    @BeforeEach
    void setUp() {
        controller = new GitHubController(githubClient);
    }

    @Test
    void rateLimit_highRemaining_ok() {
        when(githubClient.getRemainingRateLimit()).thenReturn(4000);
        ResponseEntity<Map<String, Object>> response = controller.getRateLimit();
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().get("message")).isEqualTo("OK");
    }

    @Test
    void rateLimit_lowRemaining_warns() {
        when(githubClient.getRemainingRateLimit()).thenReturn(10);
        ResponseEntity<Map<String, Object>> response = controller.getRateLimit();
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().get("message")).isEqualTo("Running low!");
    }
}
