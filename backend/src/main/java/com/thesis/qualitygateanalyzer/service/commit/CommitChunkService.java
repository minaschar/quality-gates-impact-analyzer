package com.thesis.qualitygateanalyzer.service.commit;

import com.thesis.qualitygateanalyzer.dto.response.CommitChunkResponse;

/**
 * Deterministic temporal downsampling of a repository's commit history into fixed-size chunks.
 */
public interface CommitChunkService {

    /**
     * Retrieve the full commit history of a repository's default branch and compress it
     * into a fixed number of sequential chunks. Each chunk is represented only by its
     * oldest (first) commit. History is cached in the database.
     */
    CommitChunkResponse getChunks(String organization, String repo, int chunkCount, boolean forceRefresh);
}
