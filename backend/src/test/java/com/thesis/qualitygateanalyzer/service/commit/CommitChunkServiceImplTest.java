package com.thesis.qualitygateanalyzer.service.commit;

import com.thesis.qualitygateanalyzer.dto.response.CommitChunkResponse;
import com.thesis.qualitygateanalyzer.entity.commit.CommitHistoryEntity;
import com.thesis.qualitygateanalyzer.exception.InvalidChunkCountException;
import com.thesis.qualitygateanalyzer.repository.commit.CommitHistoryRepository;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommitChunkServiceImplTest {

    @Mock
    private GitHubApiClient githubClient;
    @Mock
    private CommitHistoryRepository commitHistoryRepository;

    private CommitChunkServiceImpl service;

    private static final String OWNER = "octocat";
    private static final String REPO = "hello-world";

    @BeforeEach
    void setUp() {
        service = new CommitChunkServiceImpl(githubClient, commitHistoryRepository);
    }

    private CommitHistoryEntity commit(String sha, Instant date) {
        return CommitHistoryEntity.builder().owner(OWNER).repo(REPO).commitSha(sha).commitDate(date).build();
    }

    @Test
    void cachedHistory_isUsed_whenNotForcingRefresh() {
        List<CommitHistoryEntity> cached = List.of(
                commit("c1", Instant.parse("2024-01-01T00:00:00Z")),
                commit("c2", Instant.parse("2024-01-02T00:00:00Z"))
        );
        when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);
        when(commitHistoryRepository.findByOwnerAndRepoOrderByCommitDateAsc(OWNER, REPO)).thenReturn(cached);

        CommitChunkResponse response = service.getChunks(OWNER, REPO, 2, false);

        assertThat(response.getTotalCommits()).isEqualTo(2);
        assertThat(response.getChunks()).hasSize(2);
        verifyNoInteractions(githubClient);
    }

    @Test
    void forceRefresh_clearsCacheAndRefetchesFromGitHub() {
        List<Map<String, Object>> raw = new ArrayList<>();
        raw.add(rawCommit("newest", "2024-01-03T00:00:00Z"));
        raw.add(rawCommit("oldest", "2024-01-01T00:00:00Z"));
        when(githubClient.getAllCommits(OWNER, REPO)).thenReturn(raw);

        CommitChunkResponse response = service.getChunks(OWNER, REPO, 1, true);

        verify(commitHistoryRepository).deleteByOwnerAndRepo(OWNER, REPO);
        verify(commitHistoryRepository, never()).findByOwnerAndRepoOrderByCommitDateAsc(any(), any());

        ArgumentCaptor<List<CommitHistoryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(commitHistoryRepository).saveAll(captor.capture());
        // Raw commits come newest-first from GitHub; must be reversed to oldest-first.
        assertThat(captor.getValue()).extracting(CommitHistoryEntity::getCommitSha)
                .containsExactly("oldest", "newest");
        assertThat(response.getTotalCommits()).isEqualTo(2);
    }

    @Test
    void noCacheAndNotForcing_fetchesFromGitHub() {
        when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(false);
        List<Map<String, Object>> raw = List.of(rawCommit("sha1", "2024-01-01T00:00:00Z"));
        when(githubClient.getAllCommits(OWNER, REPO)).thenReturn(new ArrayList<>(raw));

        CommitChunkResponse response = service.getChunks(OWNER, REPO, 1, false);

        verify(commitHistoryRepository, never()).deleteByOwnerAndRepo(any(), any());
        verify(commitHistoryRepository).saveAll(anyList());
        assertThat(response.getTotalCommits()).isEqualTo(1);
    }

    @Test
    void unparseableCommitEntries_areSkipped() {
        when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(false);

        Map<String, Object> badCommit = new HashMap<>();
        badCommit.put("sha", "bad-sha");
        badCommit.put("commit", null); // triggers NPE inside toEntity -> caught, skipped

        List<Map<String, Object>> raw = new ArrayList<>();
        raw.add(badCommit);
        raw.add(rawCommit("good-sha", "2024-01-01T00:00:00Z"));
        when(githubClient.getAllCommits(OWNER, REPO)).thenReturn(raw);

        CommitChunkResponse response = service.getChunks(OWNER, REPO, 1, false);

        assertThat(response.getTotalCommits()).isEqualTo(1);
    }

    @Test
    void chunkCountGreaterThanTotalCommits_throwsInvalidChunkCountException() {
        when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);
        when(commitHistoryRepository.findByOwnerAndRepoOrderByCommitDateAsc(OWNER, REPO))
                .thenReturn(List.of(commit("c1", Instant.now())));

        InvalidChunkCountException ex = assertThrows(InvalidChunkCountException.class,
                () -> service.getChunks(OWNER, REPO, 5, false));

        assertThat(ex.getMessage()).contains("Chunk count (5)").contains("total commits (1)");
    }

    @Test
    void chunksAreComputedWithCorrectSizeAndStartCommits() {
        List<CommitHistoryEntity> cached = List.of(
                commit("c0", Instant.parse("2024-01-01T00:00:00Z")),
                commit("c1", Instant.parse("2024-01-02T00:00:00Z")),
                commit("c2", Instant.parse("2024-01-03T00:00:00Z")),
                commit("c3", Instant.parse("2024-01-04T00:00:00Z")),
                commit("c4", Instant.parse("2024-01-05T00:00:00Z"))
        );
        when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);
        when(commitHistoryRepository.findByOwnerAndRepoOrderByCommitDateAsc(OWNER, REPO)).thenReturn(cached);

        CommitChunkResponse response = service.getChunks(OWNER, REPO, 2, false);

        // chunkSize = ceil(5/2) = 3 -> chunk starts at index 0 and 3
        assertThat(response.getChunks()).hasSize(2);
        assertThat(response.getChunks().get(0).getChunkIndex()).isEqualTo(0);
        assertThat(response.getChunks().get(0).getStartCommit()).isEqualTo("c0");
        assertThat(response.getChunks().get(1).getChunkIndex()).isEqualTo(1);
        assertThat(response.getChunks().get(1).getStartCommit()).isEqualTo("c3");
    }

    @Test
    void chunkCountEqualToTotalCommits_producesOneChunkPerCommit() {
        List<CommitHistoryEntity> cached = List.of(
                commit("c0", Instant.parse("2024-01-01T00:00:00Z")),
                commit("c1", Instant.parse("2024-01-02T00:00:00Z"))
        );
        when(commitHistoryRepository.existsByOwnerAndRepo(OWNER, REPO)).thenReturn(true);
        when(commitHistoryRepository.findByOwnerAndRepoOrderByCommitDateAsc(OWNER, REPO)).thenReturn(cached);

        CommitChunkResponse response = service.getChunks(OWNER, REPO, 2, false);

        assertThat(response.getChunks()).hasSize(2);
        assertThat(response.getChunks()).extracting(CommitChunkResponse.ChunkInfo::getStartCommit)
                .containsExactly("c0", "c1");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rawCommit(String sha, String isoDate) {
        Map<String, Object> committer = new HashMap<>();
        committer.put("date", isoDate);
        Map<String, Object> commit = new HashMap<>();
        commit.put("committer", committer);
        Map<String, Object> raw = new HashMap<>();
        raw.put("sha", sha);
        raw.put("commit", commit);
        return raw;
    }
}
