package com.razeef.bugbrother.vector.service;

import com.razeef.bugbrother.github.service.CommitService;
import com.razeef.bugbrother.github.service.GitHubService;
import com.razeef.bugbrother.chunking.model.RepositoryChunk;
import com.razeef.bugbrother.grpc.gateway.GatewayGrpc;
import com.razeef.bugbrother.grpc.gateway.GatewayInsertRequest;
import com.razeef.bugbrother.grpc.gateway.GatewayKey;
import com.razeef.bugbrother.grpc.gateway.GatewayScoredResult;
import com.razeef.bugbrother.grpc.gateway.GatewaySearchRequest;
import com.razeef.bugbrother.grpc.gateway.GatewaySearchResponse;
import com.razeef.bugbrother.grpc.gateway.GatewayDeleteRequest;
import com.razeef.bugbrother.retrieval.model.VectorSearchHit;
import io.grpc.StatusRuntimeException;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.razeef.bugbrother.indexing.model.PreparedChunkSubmission;
import com.razeef.bugbrother.indexing.model.PendingChunkSubmission;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Retrieval-augmented context for the AI debug prompt, backed by the
 * self-built vectorsearch-gateway/VectorSearchEngine stack.
 *
 * The vector index itself only ever stores (client_id, label) -> vector; it
 * never stores the original text. Labels are a deterministic hash of the
 * file path, so a Search response is turned back into file content by
 * re-fetching the repo's current file listing and hashing paths the same
 * way, rather than maintaining a separate id-to-path datastore.
 *
 * Every method here is best-effort: if the gateway/coordinator/embed stack
 * isn't reachable, RAG context is simply skipped and the rest of the debug
 * pipeline behaves exactly as it did before this feature existed.
 */
@Service
@Slf4j
public class VectorSearchService {

    private static final int DEFAULT_EF = 50;
    private static final int MAX_INSERT_ATTEMPTS = 4;
    private static final long INSERT_DEADLINE_SECONDS = 10;
    private static final long INITIAL_INSERT_BACKOFF_MILLIS = 200;

    @Autowired
    private GatewayGrpc.GatewayBlockingStub gatewayStub;

    @Autowired
    private GitHubService gitHubService;

    public List<CommitService.FixedFile> searchRelated(String owner, String repo, String githubToken,
                                                         String queryText, List<CommitService.FixedFile> excludeFiles,
                                                         int k) {
        long clientId = clientIdFor(owner, repo);

        GatewaySearchResponse response;
        try {
            response = gatewayStub.search(GatewaySearchRequest.newBuilder()
                    .setText(queryText)
                    .setK(k)
                    .setEf(DEFAULT_EF)
                    .setAllowPartial(true)
                    .setClientId(clientId)
                    .build());
        } catch (StatusRuntimeException e) {
            log.warn("Vector search unavailable for {}/{}, continuing without RAG context: {}", owner, repo, e.getMessage());
            return List.of();
        }

        if (response.getResultsCount() == 0) {
            return List.of();
        }

        Set<String> excludePaths = new HashSet<>();
        for (CommitService.FixedFile f : excludeFiles) {
            excludePaths.add(f.path());
        }

        List<CommitService.FixedFile> repoFiles;
        try {
            repoFiles = gitHubService.fetchJavaFilesFromRepo(owner, repo, "", githubToken);
        } catch (Exception e) {
            log.warn("Could not fetch repo file listing for {}/{} to resolve RAG results: {}", owner, repo, e.getMessage());
            return List.of();
        }

        Map<Long, CommitService.FixedFile> byLabel = new HashMap<>();
        for (CommitService.FixedFile f : repoFiles) {
            if (!excludePaths.contains(f.path())) {
                byLabel.put(labelFor(f.path()), f);
            }
        }

        List<CommitService.FixedFile> related = new ArrayList<>();
        for (GatewayScoredResult scored : response.getResultsList()) {
            CommitService.FixedFile match = byLabel.get(scored.getKey().getLabel());
            if (match != null) {
                related.add(match);
            }
        }
        return related;
    }

    public IndexResult indexRepoFiles(
        String owner,
        String repo,
        String githubToken
    ) {
        List<CommitService.FixedFile> files;

        try {
            files = gitHubService.fetchJavaFilesFromRepo(
                    owner,
                    repo,
                    "",
                    githubToken
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not fetch repository files",
                    exception
            );
        }

        long clientId = clientIdFor(owner, repo);

        int submitted = 0;
        int failed = 0;

        for (CommitService.FixedFile file : files) {
            try {
                gatewayStub.insert(
                        GatewayInsertRequest.newBuilder()
                                .setKey(
                                        GatewayKey.newBuilder()
                                                .setClientId(clientId)
                                                .setLabel(
                                                        labelFor(file.path())
                                                )
                                                .build()
                                )
                                .setText(file.fixedContent())
                                .build()
                );

                submitted++;
            } catch (StatusRuntimeException exception) {
                failed++;

                log.warn(
                        "Could not submit {} for {}/{}: {}",
                        file.path(),
                        owner,
                        repo,
                        exception.getStatus().getCode()
                );
            }
        }

        return new IndexResult(
                files.size(),
                submitted,
                failed
        );
    }

    
    public List<VectorSearchHit> searchGeneration(
        String vectorClientId,
        String queryText,
        int resultLimit
    ) {
        if (vectorClientId == null
                || vectorClientId.isBlank()) {
            throw new IllegalArgumentException(
                    "vectorClientId is required"
            );
        }

        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException(
                    "queryText is required"
            );
        }

        if (resultLimit < 1 || resultLimit > 50) {
            throw new IllegalArgumentException(
                    "resultLimit must be between 1 and 50"
            );
        }

        long clientId;

        try {
            clientId = Long.parseUnsignedLong(
                    vectorClientId
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "vectorClientId must be an unsigned 64-bit value",
                    exception
            );
        }

        GatewaySearchResponse response;

        try {
            response = gatewayStub.search(
                    GatewaySearchRequest.newBuilder()
                            .setText(queryText)
                            .setK(resultLimit)
                            .setEf(
                                    Math.max(
                                            DEFAULT_EF,
                                            resultLimit
                                    )
                            )
                            .setAllowPartial(false)
                            .setClientId(clientId)
                            .build()
            );
        } catch (StatusRuntimeException exception) {
            throw new IllegalStateException(
                    "Vector search failed: "
                            + exception.getStatus().getCode(),
                    exception
            );
        }

        List<VectorSearchHit> hits =
                new ArrayList<>();

        for (int index = 0;
            index < response.getResultsCount();
            index++) {
            GatewayScoredResult result =
                    response.getResults(index);

            if (result.getKey().getClientId()
                    != clientId) {
                throw new IllegalStateException(
                        "Vector search returned a result "
                                + "for another client"
                );
            }

            hits.add(new VectorSearchHit(
                    Long.toUnsignedString(
                            result.getKey().getLabel()
                    ),
                    result.getDistance(),
                    index
            ));
        }

        return List.copyOf(hits);
    }



    public ChunkSubmissionResult submitChunks(
        String vectorClientId,
        List<PreparedChunkSubmission> submissions
    ) {
        if (vectorClientId == null
                || vectorClientId.isBlank()) {
            throw new IllegalArgumentException(
                    "vectorClientId is required"
            );
        }

        if (submissions == null
                || submissions.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one submission is required"
            );
        }

        long clientId;

        try {
            clientId = Long.parseUnsignedLong(
                    vectorClientId
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "vectorClientId must be an unsigned 64-bit value",
                    exception
            );
        }

        int submitted = 0;

        for (PreparedChunkSubmission submission :
                submissions) {
            RepositoryChunk chunk =
                    submission.chunk();

            GatewayInsertRequest request =
                    GatewayInsertRequest.newBuilder()
                            .setKey(GatewayKey.newBuilder()
                                    .setClientId(clientId)
                                    .setLabel(chunk.vectorLabel())
                                    .build())
                            .setText(chunk.embeddingText())
                            .setCorrelationId(submission
                                    .submissionEventId()
                                    .toString())
                            .build();

            try {
                insertWithRetry(request);

                submitted++;
            } catch (StatusRuntimeException exception) {
                throw new IllegalStateException(
                        "Gateway rejected chunk "
                                + chunk.chunkId()
                                + " from "
                                + chunk.path()
                                + ": "
                                + exception
                                        .getStatus()
                                        .getCode(),
                        exception
                );
            }
        }

        return new ChunkSubmissionResult(
                submissions.size(),
                submitted
        );
    }

    private void insertWithRetry(GatewayInsertRequest request) {
        for (int attempt = 1; attempt <= MAX_INSERT_ATTEMPTS; attempt++) {
            try {
                gatewayStub.withDeadlineAfter(
                        INSERT_DEADLINE_SECONDS,
                        TimeUnit.SECONDS
                ).insert(request);
                return;
            } catch (StatusRuntimeException exception) {
                if (attempt == MAX_INSERT_ATTEMPTS
                        || !isTransientInsertFailure(
                                exception.getStatus().getCode())) {
                    throw exception;
                }

                try {
                    Thread.sleep(
                            INITIAL_INSERT_BACKOFF_MILLIS
                                    << (attempt - 1)
                    );
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Interrupted while retrying vector insertion",
                            interrupted
                    );
                }
            }
        }
    }

    public void submitStoredChunk(String vectorClientId,
            PendingChunkSubmission submission) {
        if (submission == null || submission.submissionEventId() == null
                || submission.embeddingText() == null) {
            throw new IllegalArgumentException("Stored chunk submission is incomplete");
        }

        GatewayInsertRequest request = GatewayInsertRequest.newBuilder()
                .setKey(GatewayKey.newBuilder()
                        .setClientId(Long.parseUnsignedLong(vectorClientId))
                        .setLabel(Long.parseUnsignedLong(
                                submission.vectorLabel()))
                        .build())
                .setText(submission.embeddingText())
                .setCorrelationId(submission.submissionEventId().toString())
                .build();
        insertWithRetry(request);
    }

    private boolean isTransientInsertFailure(Status.Code code) {
        return code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.RESOURCE_EXHAUSTED;
    }

    public void deleteVectors(
            String vectorClientId,
            List<String> vectorLabels,
            UUID cleanupEventId
    ) {
        if (vectorClientId == null || vectorClientId.isBlank()) {
            throw new IllegalArgumentException(
                    "vectorClientId is required"
            );
        }

        long clientId;
        try {
            clientId = Long.parseUnsignedLong(vectorClientId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "vectorClientId must be an unsigned 64-bit value",
                    exception
            );
        }

        if (vectorLabels == null) {
            throw new IllegalArgumentException(
                    "vectorLabels are required"
            );
        }

        for (String vectorLabel : vectorLabels) {
            long label;
            try {
                label = Long.parseUnsignedLong(vectorLabel);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Invalid vector label: " + vectorLabel,
                        exception
                );
            }

            GatewayDeleteRequest request =
                    GatewayDeleteRequest.newBuilder()
                            .setKey(GatewayKey.newBuilder()
                                    .setClientId(clientId)
                                    .setLabel(label)
                                    .build())
                            .setCorrelationId(
                                    cleanupEventId + ":" + vectorLabel
                            )
                            .build();

            deleteWithRateLimitRetry(request);
        }
    }

    private void deleteWithRateLimitRetry(
            GatewayDeleteRequest request
    ) {
        while (true) {
            try {
                gatewayStub.delete(request);
                return;
            } catch (StatusRuntimeException exception) {
                if (exception.getStatus().getCode()
                        != Status.Code.RESOURCE_EXHAUSTED) {
                    throw exception;
                }

                try {
                    Thread.sleep(1100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Interrupted while waiting to retry vector deletion",
                            interrupted
                    );
                }
            }
        }
    }

    private long clientIdFor(String owner, String repo) {
        return fnv1a64(owner + "/" + repo);
    }

    private long labelFor(String path) {
        return fnv1a64(path);
    }

    private static long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        long prime = 0x100000001b3L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= prime;
        }
        return hash;
    }

    public record IndexResult(
        int totalFiles,
        int submittedFiles,
        int failedFiles
    ) {
    }


    public record ChunkSubmissionResult(
        int totalChunks,
        int submittedChunks
    ) {
    }

}
