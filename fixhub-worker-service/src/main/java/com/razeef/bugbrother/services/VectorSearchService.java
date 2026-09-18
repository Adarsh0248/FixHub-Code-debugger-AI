package com.razeef.bugbrother.services;

import com.razeef.bugbrother.grpc.gateway.GatewayGrpc;
import com.razeef.bugbrother.grpc.gateway.GatewayInsertRequest;
import com.razeef.bugbrother.grpc.gateway.GatewayKey;
import com.razeef.bugbrother.grpc.gateway.GatewayScoredResult;
import com.razeef.bugbrother.grpc.gateway.GatewaySearchRequest;
import com.razeef.bugbrother.grpc.gateway.GatewaySearchResponse;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
}
