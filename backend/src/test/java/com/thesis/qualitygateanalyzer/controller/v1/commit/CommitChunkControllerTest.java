package com.thesis.qualitygateanalyzer.controller.v1.commit;

import com.thesis.qualitygateanalyzer.dto.response.ApiResponse;
import com.thesis.qualitygateanalyzer.dto.response.CommitChunkResponse;
import com.thesis.qualitygateanalyzer.exception.InvalidChunkCountException;
import com.thesis.qualitygateanalyzer.service.commit.CommitChunkService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommitChunkControllerTest {

    @Mock
    private CommitChunkService commitChunkService;

    private CommitChunkController controller;

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";

    @BeforeEach
    void setUp() {
        controller = new CommitChunkController(commitChunkService);
    }

    @Test
    void success_returnsOkWithChunks() {
        CommitChunkResponse chunkResponse = CommitChunkResponse.builder()
                .organization(OWNER).repo(REPO).chunkCount(2).totalCommits(10).chunks(java.util.List.of()).build();
        when(commitChunkService.getChunks(OWNER, REPO, 2, false)).thenReturn(chunkResponse);

        ResponseEntity<?> response = controller.getCommitChunks(OWNER, REPO, 2, false);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        Assertions.assertNotNull(body);
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isEqualTo(chunkResponse);
    }

    @Test
    void illegalArgument_returnsBadRequest() {
        when(commitChunkService.getChunks(OWNER, REPO, 0, false))
                .thenThrow(new IllegalArgumentException("chunkCount must be positive"));

        ResponseEntity<?> response = controller.getCommitChunks(OWNER, REPO, 0, false);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        Assertions.assertNotNull(body);
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getError()).isEqualTo("chunkCount must be positive");
    }

    @Test
    void invalidChunkCount_isRethrownForGlobalHandler() {
        when(commitChunkService.getChunks(OWNER, REPO, 999, false))
                .thenThrow(new InvalidChunkCountException("Chunk count too large"));

        assertThrows(InvalidChunkCountException.class, () -> controller.getCommitChunks(OWNER, REPO, 999, false));
    }

    @Test
    void unexpectedException_returns500() {
        when(commitChunkService.getChunks(OWNER, REPO, 2, false)).thenThrow(new RuntimeException("db down"));

        ResponseEntity<?> response = controller.getCommitChunks(OWNER, REPO, 2, false);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        Assertions.assertNotNull(body);
        assertThat(body.getError()).contains("db down");
    }
}
