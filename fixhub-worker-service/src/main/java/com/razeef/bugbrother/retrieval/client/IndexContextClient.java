package com.razeef.bugbrother.retrieval.client;

import com.razeef.bugbrother.retrieval.exception.ContextRetrievalException;
import com.razeef.bugbrother.retrieval.model.VectorContext;
import com.razeef.bugbrother.retrieval.model.VectorSearchHit;
import com.razeef.bugbrother.retrieval.model.DependencyExpansion;
import com.razeef.bugbrother.retrieval.model.ResolvedSourceFiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.UUID;

@Service
public class IndexContextClient {

    private static final String WORKER_KEY_HEADER =
            "X-BugBrother-Worker-Key";

    private final WebClient webClient;
    private final String workerKey;

    public IndexContextClient(
            WebClient.Builder builder,
            @Value("${bugbrother.ingestion.base-url}")
            String ingestionBaseUrl,
            @Value("${bugbrother.internal.worker-key}")
            String workerKey
    ) {
        this.webClient = builder
                .baseUrl(ingestionBaseUrl)
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        "application/json"
                )
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        "application/json"
                )
                .build();

        this.workerKey = workerKey;
    }

    public VectorContext resolve(
            UUID generationId,
            String vectorClientId,
            List<VectorSearchHit> hits
    ) {
        if (generationId == null) {
            throw new IllegalArgumentException(
                    "generationId is required"
            );
        }

        if (hits == null || hits.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one vector hit is required"
            );
        }

        ResolveRequest request = new ResolveRequest(
                vectorClientId,
                hits.stream()
                        .map(hit -> new HitRequest(
                                hit.vectorLabel(),
                                hit.distance(),
                                hit.rank()
                        ))
                        .toList()
        );

        try {
            VectorContext response = webClient.post()
                    .uri(
                            "/internal/index-generations/"
                                    + generationId
                                    + "/resolve-vector-hits"
                    )
                    .header(
                            WORKER_KEY_HEADER,
                            workerKey
                    )
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(VectorContext.class)
                    .block();

            if (response == null) {
                throw new ContextRetrievalException(
                        "Ingestion returned an empty vector context"
                );
            }

            return response;
        } catch (WebClientResponseException exception) {
            throw new ContextRetrievalException(
                    "Ingestion rejected vector context request "
                            + "with HTTP "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (ContextRetrievalException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ContextRetrievalException(
                    "Could not resolve vector results",
                    exception
            );
        }
    }

    public DependencyExpansion expandDependencies(
            UUID generationId,
            String vectorClientId,
            List<UUID> seedFileIds,
            int maxDepth,
            int maxFiles
    ) {
        if (generationId == null) {
            throw new IllegalArgumentException(
                    "generationId is required"
            );
        }
        if (seedFileIds == null || seedFileIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one seed file is required"
            );
        }

        try {
            DependencyExpansion response = webClient.post()
                    .uri(
                            "/internal/index-generations/"
                                    + generationId
                                    + "/expand-dependencies"
                    )
                    .header(WORKER_KEY_HEADER, workerKey)
                    .bodyValue(new ExpansionRequest(
                            vectorClientId,
                            seedFileIds,
                            maxDepth,
                            maxFiles
                    ))
                    .retrieve()
                    .bodyToMono(DependencyExpansion.class)
                    .block();

            if (response == null) {
                throw new ContextRetrievalException(
                        "Ingestion returned an empty dependency expansion"
                );
            }
            return response;
        } catch (WebClientResponseException exception) {
            throw new ContextRetrievalException(
                    "Ingestion rejected dependency expansion with HTTP "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (ContextRetrievalException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ContextRetrievalException(
                    "Could not expand repository dependencies",
                    exception
            );
        }
    }

    public ResolvedSourceFiles resolveFiles(
            UUID generationId,
            String vectorClientId,
            List<String> paths
    ) {
        if (generationId == null) {
            throw new IllegalArgumentException(
                    "generationId is required"
            );
        }
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one source path is required"
            );
        }

        try {
            ResolvedSourceFiles response = webClient.post()
                    .uri(
                            "/internal/index-generations/"
                                    + generationId
                                    + "/resolve-files"
                    )
                    .header(WORKER_KEY_HEADER, workerKey)
                    .bodyValue(new ResolveFilesRequest(
                            vectorClientId,
                            paths
                    ))
                    .retrieve()
                    .bodyToMono(ResolvedSourceFiles.class)
                    .block();

            if (response == null) {
                throw new ContextRetrievalException(
                        "Ingestion returned an empty file response"
                );
            }
            return response;
        } catch (WebClientResponseException exception) {
            throw new ContextRetrievalException(
                    "Ingestion rejected requested files with HTTP "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (ContextRetrievalException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ContextRetrievalException(
                    "Could not resolve requested source files",
                    exception
            );
        }
    }

    private record ResolveRequest(
            String vectorClientId,
            List<HitRequest> hits
    ) {
    }

    private record HitRequest(
            String vectorLabel,
            float distance,
            int rank
    ) {
    }

    private record ExpansionRequest(
            String vectorClientId,
            List<UUID> seedFileIds,
            int maxDepth,
            int maxFiles
    ) {
    }

    private record ResolveFilesRequest(
            String vectorClientId,
            List<String> paths
    ) {
    }
}
