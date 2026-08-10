package com.thesis.qualitygateanalyzer.service.commit;

import com.thesis.qualitygateanalyzer.dto.response.CommitChunkResponse;
import com.thesis.qualitygateanalyzer.dto.response.CommitChunkResponse.ChunkInfo;
import com.thesis.qualitygateanalyzer.entity.commit.CommitHistoryEntity;
import com.thesis.qualitygateanalyzer.exception.InvalidChunkCountException;
import com.thesis.qualitygateanalyzer.repository.commit.CommitHistoryRepository;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Default {@link CommitChunkService} implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommitChunkServiceImpl implements CommitChunkService {

    private final GitHubApiClient githubClient;
    private final CommitHistoryRepository commitHistoryRepository;

    @Override
    @Transactional
    public CommitChunkResponse getChunks(String organization, String repo, int chunkCount, boolean forceRefresh) {
        if (chunkCount <= 0) {
            throw new IllegalArgumentException("chunkCount must be positive");
        }

        List<CommitHistoryEntity> commits = resolveCommits(organization, repo, forceRefresh);

        int totalCommits = commits.size();
        if (totalCommits < chunkCount) {
            throw new InvalidChunkCountException(
                    "Chunk count (" + chunkCount + ") is greater than total commits (" +
                            totalCommits + "). Please reduce chunkCount or choose a smaller value."
            );
        }

        List<ChunkInfo> chunks = new ArrayList<>();

        for (int i = 0; i < chunkCount; i++) {
            int startIdx = (int) ((long) i * totalCommits / chunkCount);
            CommitHistoryEntity head = commits.get(startIdx);
            chunks.add(ChunkInfo.builder()
                    .chunkIndex(i)
                    .startCommit(head.getCommitSha())
                    .startDate(head.getCommitDate())
                    .build());
        }

        return CommitChunkResponse.builder()
                .organization(organization)
                .repo(repo)
                .chunkCount(chunkCount)
                .totalCommits(totalCommits)
                .chunks(chunks)
                .build();
    }

    private List<CommitHistoryEntity> resolveCommits(String organization, String repo, boolean forceRefresh) {
        if (!forceRefresh && commitHistoryRepository.existsByOwnerAndRepo(organization, repo)) {
            log.info("Returning cached commit history for {}/{}", organization, repo);
            return commitHistoryRepository.findByOwnerAndRepoOrderByCommitDateAsc(organization, repo);
        }

        if (forceRefresh) {
            log.info("Force refresh: clearing cached commit history for {}/{}", organization, repo);
            commitHistoryRepository.deleteByOwnerAndRepo(organization, repo);
        }

        log.info("Fetching full commit history for {}/{} from GitHub", organization, repo);
        List<Map<String, Object>> raw = githubClient.getAllCommits(organization, repo);

        // GitHub returns newest-first; reverse to get chronological (oldest-first) order
        Collections.reverse(raw);

        List<CommitHistoryEntity> entities = raw.stream()
                .map(c -> toEntity(organization, repo, c))
                .filter(Objects::nonNull)
                .toList();

        commitHistoryRepository.saveAll(entities);
        log.info("Stored {} commits for {}/{}", entities.size(), organization, repo);
        return entities;
    }

    @SuppressWarnings("unchecked")
    private CommitHistoryEntity toEntity(String owner, String repo, Map<String, Object> raw) {
        try {
            String sha = (String) raw.get("sha");
            Map<String, Object> commit = (Map<String, Object>) raw.get("commit");
            Map<String, Object> committer = (Map<String, Object>) commit.get("committer");
            String dateStr = (String) committer.get("date");
            Instant commitDate = Instant.parse(dateStr);

            return CommitHistoryEntity.builder()
                    .owner(owner)
                    .repo(repo)
                    .commitSha(sha)
                    .commitDate(commitDate)
                    .build();
        } catch (Exception e) {
            log.warn("Skipping unparseable commit entry: {}", e.getMessage());
            return null;
        }
    }
}
