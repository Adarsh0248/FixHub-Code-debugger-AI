package com.razeef.bugbrother.indexing.client;

import com.razeef.bugbrother.indexing.exception.ManifestSubmissionException;

import com.razeef.bugbrother.chunking.model.RepositoryChunk;
import com.razeef.bugbrother.source.model.RepositorySourceFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.razeef.bugbrother.indexing.model.PreparedChunkSubmission;
import com.razeef.bugbrother.indexing.model.IndexCleanupPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ManifestSubmissionClient {

    private static final String WORKER_KEY_HEADER =
            "X-BugBrother-Worker-Key";

    private static final int FILE_BATCH_SIZE = 20;
    private static final int CHUNK_BATCH_SIZE = 100;

    private final WebClient webClient;
    private final String workerKey;

    public ManifestSubmissionClient(
            WebClient.Builder webClientBuilder,
            @Value("${bugbrother.ingestion.base-url}")
            String ingestionBaseUrl,
            @Value("${bugbrother.internal.worker-key}")
            String workerKey
    ) {
        this.webClient = webClientBuilder
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

    public void submitManifest(
            UUID generationId,
            List<RepositorySourceFile> files,
            List<RepositoryChunk> chunks
    ) {
        if (generationId == null) {
            throw new IllegalArgumentException(
                    "generationId is required"
            );
        }

        if (files == null || chunks == null) {
            throw new IllegalArgumentException(
                    "Files and chunks are required"
            );
        }

        Map<String, String> languageByPath =
                chunks.stream()
                        .collect(Collectors.toMap(
                                RepositoryChunk::path,
                                RepositoryChunk::language,
                                (first, ignored) -> first
                        ));

        List<FilePayload> filePayloads =
                files.stream()
                        .map(file -> new FilePayload(
                                file.path(),
                                languageByPath.getOrDefault(
                                        file.path(),
                                        "text"
                                ),
                                file.gitBlobSha(),
                                file.contentSha256(),
                                file.sizeBytes(),
                                file.content()
                        ))
                        .toList();

        List<ChunkPayload> chunkPayloads =
                chunks.stream()
                        .map(chunk -> new ChunkPayload(
                                chunk.path(),

                                chunk.chunkId(),
                                Long.toUnsignedString(
                                        chunk.vectorLabel()
                                ),

                                chunk.path(),
                                chunk.language(),
                                chunk.symbol(),

                                chunk.startLine(),
                                chunk.endLine(),

                                chunk.fileContentSha256(),
                                chunk.chunkContentSha256(),
                                chunk.chunkerVersion(),

                                chunk.sourceContent(),
                                chunk.embeddingText()
                        ))
                        .toList();

        submitBatches(
                filePayloads,
                FILE_BATCH_SIZE,
                batch -> {
                    post(
                            "/internal/index-generations/"
                                    + generationId
                                    + "/manifest/files",
                            new FilesRequest(batch)
                    );

                    return null;
                }
        );

        submitBatches(
                chunkPayloads,
                CHUNK_BATCH_SIZE,
                batch -> {
                    post(
                            "/internal/index-generations/"
                                    + generationId
                                    + "/manifest/chunks",
                            new ChunksRequest(batch)
                    );

                    return null;
                }
        );

        post(
                "/internal/index-generations/"
                        + generationId
                        + "/manifest/complete",
                new CompleteRequest(
                        files.size(),
                        chunks.size()
                )
        );
    }

    public void registerSubmissions(
        UUID generationId,
        List<PreparedChunkSubmission> submissions
        ) {
        if (generationId == null) {
                throw new IllegalArgumentException(
                        "generationId is required"
                );
        }

        if (submissions == null || submissions.isEmpty()) {
                throw new IllegalArgumentException(
                        "At least one submission is required"
                );
        }

        List<SubmissionPayload> payloads =
                submissions.stream()
                        .map(submission ->
                                new SubmissionPayload(
                                        submission
                                                .chunk()
                                                .chunkId(),
                                        submission
                                                .submissionEventId()
                                )
                        )
                        .toList();

        submitBatches(
                payloads,
                CHUNK_BATCH_SIZE,
                batch -> {
                        post(
                                "/internal/index-generations/"
                                        + generationId
                                        + "/submissions",
                                new SubmissionsRequest(batch)
                        );

                        return null;
                }
        );
        }


    public void failGeneration(
        UUID generationId,
        String errorCode,
        String errorMessage,
        boolean vectorsMayExist
        ) {
        if (generationId == null) {
                throw new IllegalArgumentException(
                        "generationId is required"
                );
        }

        post(
                "/internal/index-generations/"
                        + generationId
                        + "/fail",
                new FailureRequest(
                        errorCode,
                        errorMessage,
                        vectorsMayExist
                )
        );
        }

    public void discardPreparedGeneration(UUID generationId) {
        postWithoutBody(
                "/internal/index-generations/"
                        + generationId
                        + "/discard-prepared"
        );
    }

    public void markVectorSubmissionStarted(UUID generationId) {
        postWithoutBody(
                "/internal/index-generations/"
                        + generationId
                        + "/submission-started"
        );
    }

    public IndexCleanupPlan fetchCleanupPlan(UUID generationId) {
        try {
            return webClient.get()
                    .uri("/internal/index-generations/"
                            + generationId
                            + "/cleanup-plan")
                    .header(WORKER_KEY_HEADER, workerKey)
                    .retrieve()
                    .bodyToMono(IndexCleanupPlan.class)
                    .block();
        } catch (WebClientResponseException exception) {
            throw new ManifestSubmissionException(
                    "Ingestion rejected cleanup plan request with HTTP "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new ManifestSubmissionException(
                    "Could not fetch index cleanup plan",
                    exception
            );
        }
    }

    public void completeCleanup(UUID generationId) {
        postWithoutBody(
                "/internal/index-generations/"
                        + generationId
                        + "/cleanup-complete"
        );
    }




    private <T> void submitBatches(
            List<T> items,
            int batchSize,
            Function<List<T>, Void> submitter
    ) {
        for (int start = 0;
             start < items.size();
             start += batchSize) {
            int end = Math.min(
                    start + batchSize,
                    items.size()
            );

            submitter.apply(
                    new ArrayList<>(
                            items.subList(start, end)
                    )
            );
        }
    }

    private void post(
            String path,
            Object body
    ) {
        try {
            webClient.post()
                    .uri(path)
                    .header(
                            WORKER_KEY_HEADER,
                            workerKey
                    )
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException exception) {
            throw new ManifestSubmissionException(
                    "Ingestion rejected manifest request with HTTP "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new ManifestSubmissionException(
                    "Could not submit index manifest",
                    exception
            );
        }
    }

    private void postWithoutBody(String path) {
        try {
            webClient.post()
                    .uri(path)
                    .header(WORKER_KEY_HEADER, workerKey)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException exception) {
            throw new ManifestSubmissionException(
                    "Ingestion rejected cleanup request with HTTP "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new ManifestSubmissionException(
                    "Could not complete index cleanup request",
                    exception
            );
        }
    }

    private record FilesRequest(
            List<FilePayload> files
    ) {
    }

    private record ChunksRequest(
            List<ChunkPayload> chunks
    ) {
    }

    private record CompleteRequest(
            int expectedFiles,
            int expectedChunks
    ) {
    }

    private record FilePayload(
            String path,
            String language,
            String gitBlobSha,
            String contentSha256,
            long sizeBytes,
            String content
    ) {
    }

    private record FailureRequest(
        String errorCode,
        String errorMessage,
        boolean vectorsMayExist
        ) {
        }

    private record ChunkPayload(
            String filePath,

            String chunkId,
            String vectorLabel,

            String path,
            String language,
            String symbol,

            int startLine,
            int endLine,

            String fileContentSha256,
            String chunkContentSha256,
            String chunkerVersion,

            String sourceContent,
            String embeddingText
    ) {
    }

    private record SubmissionsRequest(
        List<SubmissionPayload> submissions
        ) {
        }

private record SubmissionPayload(
        String chunkId,
        UUID submissionEventId
        ) {
        }
}
