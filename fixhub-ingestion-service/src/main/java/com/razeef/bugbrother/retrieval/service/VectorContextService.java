package com.razeef.bugbrother.retrieval.service;

import com.razeef.bugbrother.indexes.model.ChunkIndexStatus;
import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;
import com.razeef.bugbrother.indexes.model.IndexedChunkEntity;
import com.razeef.bugbrother.indexes.model.IndexedSourceFileEntity;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;
import com.razeef.bugbrother.indexes.repository.IndexedChunkRepository;
import com.razeef.bugbrother.indexes.repository.IndexedSourceFileRepository;
import com.razeef.bugbrother.retrieval.dto.request.ResolveVectorHitsRequest;
import com.razeef.bugbrother.retrieval.dto.request.VectorHitRequest;
import com.razeef.bugbrother.retrieval.dto.request.ResolveSourceFilesRequest;
import com.razeef.bugbrother.retrieval.dto.response.RetrievedChunkResponse;
import com.razeef.bugbrother.retrieval.dto.response.RetrievedSourceFileResponse;
import com.razeef.bugbrother.retrieval.dto.response.VectorContextResponse;
import com.razeef.bugbrother.retrieval.dto.response.ResolvedSourceFileResponse;
import com.razeef.bugbrother.retrieval.dto.response.ResolvedSourceFilesResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashSet;

@Service
public class VectorContextService {

    private static final int MAX_VECTOR_HITS = 50;

    private final IndexGenerationRepository generationRepository;
    private final IndexedChunkRepository chunkRepository;
    private final IndexedSourceFileRepository fileRepository;

    public VectorContextService(
            IndexGenerationRepository generationRepository,
            IndexedChunkRepository chunkRepository,
            IndexedSourceFileRepository fileRepository
    ) {
        this.generationRepository = generationRepository;
        this.chunkRepository = chunkRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional(readOnly = true)
    public VectorContextResponse resolve(
            UUID generationId,
            ResolveVectorHitsRequest request
    ) {
        validateRequest(generationId, request);

        IndexGenerationEntity generation = generationRepository
                .findById(generationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Generation not found: "
                                        + generationId
                        )
                );

        validateGeneration(generation, request);

        List<VectorHitRequest> orderedHits =
                normalizeHits(request.hits());

        Collection<String> labels = orderedHits.stream()
                .map(VectorHitRequest::vectorLabel)
                .toList();

        List<IndexedChunkEntity> chunks = chunkRepository
                .findByGenerationIdAndVectorLabelInAndStatus(
                        generationId,
                        labels,
                        ChunkIndexStatus.INDEXED
                );

        Map<String, IndexedChunkEntity> chunkByLabel =
                new HashMap<>();

        for (IndexedChunkEntity chunk : chunks) {
            chunkByLabel.put(
                    chunk.getVectorLabel(),
                    chunk
            );
        }

        ensureEveryHitWasResolved(
                orderedHits,
                chunkByLabel
        );

        Map<UUID, List<ResolvedChunk>> chunksByFile =
                new LinkedHashMap<>();

        for (VectorHitRequest hit : orderedHits) {
            IndexedChunkEntity chunk =
                    chunkByLabel.get(hit.vectorLabel());

            chunksByFile
                    .computeIfAbsent(
                            chunk.getFileId(),
                            ignored -> new ArrayList<>()
                    )
                    .add(new ResolvedChunk(
                            chunk,
                            hit
                    ));
        }

        List<IndexedSourceFileEntity> files =
                fileRepository
                        .findByGenerationIdAndFileIdIn(
                                generationId,
                                chunksByFile.keySet()
                        );

        Map<UUID, IndexedSourceFileEntity> fileById =
                new HashMap<>();

        for (IndexedSourceFileEntity file : files) {
            fileById.put(file.getFileId(), file);
        }

        if (fileById.size() != chunksByFile.size()) {
            throw new IllegalStateException(
                    "One or more retrieved chunks point to missing files"
            );
        }

        List<RetrievedSourceFileResponse> responseFiles =
                new ArrayList<>();

        for (Map.Entry<UUID, List<ResolvedChunk>> entry
                : chunksByFile.entrySet()) {
            IndexedSourceFileEntity file =
                    fileById.get(entry.getKey());

            List<ResolvedChunk> matched =
                    entry.getValue();

            matched.sort(
                    Comparator.comparingInt(
                            resolved ->
                                    resolved.hit().rank()
                    )
            );

            VectorHitRequest bestHit =
                    matched.getFirst().hit();

            List<RetrievedChunkResponse> responseChunks =
                    matched.stream()
                            .map(this::toChunkResponse)
                            .toList();

            responseFiles.add(
                    new RetrievedSourceFileResponse(
                            file.getFileId(),
                            file.getPath(),
                            file.getLanguage(),

                            file.getGitBlobSha(),
                            file.getContentSha256(),
                            file.getContent(),

                            bestHit.distance(),
                            bestHit.rank(),

                            responseChunks
                    )
            );
        }

        responseFiles.sort(
                Comparator.comparingInt(
                        RetrievedSourceFileResponse::bestRank
                )
        );

        return new VectorContextResponse(
                generationId,
                generation.getVectorClientId(),
                generation.getCommitSha(),

                request.hits().size(),
                orderedHits.size(),

                List.copyOf(responseFiles)
        );
    }

    @Transactional(readOnly = true)
    public ResolvedSourceFilesResponse resolveFiles(
            UUID generationId,
            ResolveSourceFilesRequest request
    ) {
        if (generationId == null || request == null) {
            throw new IllegalArgumentException(
                    "Generation and file request are required"
            );
        }
        if (request.vectorClientId() == null
                || request.vectorClientId().isBlank()) {
            throw new IllegalArgumentException(
                    "vectorClientId is required"
            );
        }
        if (request.paths() == null || request.paths().isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one source path is required"
            );
        }
        if (request.paths().size() > 10) {
            throw new IllegalArgumentException(
                    "A file request cannot exceed 10 paths"
            );
        }

        IndexGenerationEntity generation = generationRepository
                .findById(generationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Generation not found: " + generationId
                ));

        if (generation.getStatus() != IndexGenerationStatus.READY) {
            throw new IllegalStateException(
                    "Only a ready generation can resolve source files"
            );
        }
        if (!generation.getVectorClientId()
                .equals(request.vectorClientId())) {
            throw new IllegalArgumentException(
                    "Vector client ID does not match generation"
            );
        }

        List<String> paths = request.paths().stream()
                .map(this::normalizeRequestedPath)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(
                                LinkedHashSet::new
                        ),
                        List::copyOf
                ));

        List<IndexedSourceFileEntity> found = fileRepository
                .findByGenerationIdAndPathIn(generationId, paths);
        Map<String, IndexedSourceFileEntity> byPath = new HashMap<>();
        found.forEach(file -> byPath.put(file.getPath(), file));

        List<String> missing = paths.stream()
                .filter(path -> !byPath.containsKey(path))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Requested files do not exist in this generation: "
                            + missing
            );
        }

        List<ResolvedSourceFileResponse> files = paths.stream()
                .map(byPath::get)
                .map(file -> new ResolvedSourceFileResponse(
                        file.getFileId(),
                        file.getPath(),
                        file.getLanguage(),
                        file.getGitBlobSha(),
                        file.getContentSha256(),
                        file.getContent()
                ))
                .toList();

        return new ResolvedSourceFilesResponse(
                generationId,
                generation.getVectorClientId(),
                generation.getCommitSha(),
                files
        );
    }

    private String normalizeRequestedPath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Requested source paths cannot be blank"
            );
        }

        String path = value.trim().replace('\\', '/');
        while (path.startsWith("./")) {
            path = path.substring(2);
        }

        if (path.isBlank()
                || path.startsWith("/")
                || path.matches("^[A-Za-z]:.*")
                || java.util.Arrays.asList(path.split("/", -1))
                        .contains("..")) {
            throw new IllegalArgumentException(
                    "Unsafe repository path: " + value
            );
        }

        return path;
    }

    private List<VectorHitRequest> normalizeHits(
            List<VectorHitRequest> hits
    ) {
        Map<String, VectorHitRequest> unique =
                new LinkedHashMap<>();

        hits.stream()
                .sorted(
                        Comparator.comparingInt(
                                VectorHitRequest::rank
                        )
                )
                .forEach(hit ->
                        unique.putIfAbsent(
                                hit.vectorLabel(),
                                hit
                        )
                );

        return List.copyOf(unique.values());
    }

    private RetrievedChunkResponse toChunkResponse(
            ResolvedChunk resolved
    ) {
        IndexedChunkEntity chunk =
                resolved.chunk();

        VectorHitRequest hit =
                resolved.hit();

        return new RetrievedChunkResponse(
                chunk.getChunkId(),
                chunk.getVectorLabel(),

                chunk.getPath(),
                chunk.getSymbol(),

                chunk.getStartLine(),
                chunk.getEndLine(),

                chunk.getSourceContent(),

                hit.distance(),
                hit.rank()
        );
    }

    private void validateRequest(
            UUID generationId,
            ResolveVectorHitsRequest request
    ) {
        if (generationId == null) {
            throw new IllegalArgumentException(
                    "generationId is required"
            );
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Retrieval request is required"
            );
        }

        if (request.vectorClientId() == null
                || request.vectorClientId().isBlank()) {
            throw new IllegalArgumentException(
                    "vectorClientId is required"
            );
        }

        if (request.hits() == null
                || request.hits().isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one vector hit is required"
            );
        }

        if (request.hits().size() > MAX_VECTOR_HITS) {
            throw new IllegalArgumentException(
                    "A retrieval request cannot exceed "
                            + MAX_VECTOR_HITS
                            + " vector hits"
            );
        }

        for (VectorHitRequest hit : request.hits()) {
            validateHit(hit);
        }
    }

    private void validateHit(VectorHitRequest hit) {
        if (hit == null) {
            throw new IllegalArgumentException(
                    "Vector hit cannot be null"
            );
        }

        if (hit.vectorLabel() == null
                || hit.vectorLabel().isBlank()) {
            throw new IllegalArgumentException(
                    "Vector label is required"
            );
        }

        try {
            Long.parseUnsignedLong(hit.vectorLabel());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid vector label: "
                            + hit.vectorLabel(),
                    exception
            );
        }

        if (!Float.isFinite(hit.distance())) {
            throw new IllegalArgumentException(
                    "Vector distance must be finite"
            );
        }

        if (hit.rank() < 0) {
            throw new IllegalArgumentException(
                    "Vector rank cannot be negative"
            );
        }
    }

    private void validateGeneration(
            IndexGenerationEntity generation,
            ResolveVectorHitsRequest request
    ) {
        if (generation.getStatus()
                != IndexGenerationStatus.READY) {
            throw new IllegalStateException(
                    "Only a ready generation can be retrieved"
            );
        }

        if (!generation.getVectorClientId()
                .equals(request.vectorClientId())) {
            throw new IllegalArgumentException(
                    "Vector client ID does not match generation"
            );
        }
    }

    private void ensureEveryHitWasResolved(
            List<VectorHitRequest> hits,
            Map<String, IndexedChunkEntity> chunkByLabel
    ) {
        List<String> missing = hits.stream()
                .map(VectorHitRequest::vectorLabel)
                .filter(label ->
                        !chunkByLabel.containsKey(label)
                )
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Vector results contain labels missing "
                            + "from the active manifest: "
                            + missing
            );
        }
    }

    private record ResolvedChunk(
            IndexedChunkEntity chunk,
            VectorHitRequest hit
    ) {
    }
}
