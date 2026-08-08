package com.thesis.qualitygateanalyzer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class CommitChunkResponse {

    private String organization;
    private String repo;
    private int chunkCount;
    private int totalCommits;
    private List<ChunkInfo> chunks;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ChunkInfo {
        private int chunkIndex;
        private String startCommit;
        private Instant startDate;
    }
}
