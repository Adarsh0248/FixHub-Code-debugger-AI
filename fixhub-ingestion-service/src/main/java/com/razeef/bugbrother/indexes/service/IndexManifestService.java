package com.razeef.bugbrother.indexes.service;

import com.razeef.bugbrother.indexes.dto.request.ManifestChunkInput;
import com.razeef.bugbrother.indexes.dto.request.ManifestFileInput;
import com.razeef.bugbrother.indexes.dto.response.ManifestRegistrationResult;
import com.razeef.bugbrother.indexes.dto.response.IndexSubmissionRecoveryState;
import com.razeef.bugbrother.indexes.dto.response.PendingChunkSubmissionResponse;
import com.razeef.bugbrother.indexes.model.ChunkIndexStatus;
import com.razeef.bugbrother.indexes.model.IndexedChunkEntity;
import com.razeef.bugbrother.indexes.model.IndexedSourceFileEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;
import com.razeef.bugbrother.indexes.repository.IndexedChunkRepository;
import com.razeef.bugbrother.indexes.repository.IndexedSourceFileRepository;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;
import com.razeef.bugbrother.indexes.dto.request.ChunkSubmissionInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import com.razeef.bugbrother.dependencies.service.DependencyGraphService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IndexManifestService {

    @Transactional(readOnly = true)
    public IndexSubmissionRecoveryState recoveryState(UUID generationId) {
        IndexGenerationEntity generation = generationRepository.findById(
                generationId).orElseThrow(() -> new IllegalArgumentException(
                        "Generation not found: " + generationId));
        return IndexSubmissionRecoveryState.from(generation);
    }

    @Transactional(readOnly = true)
    public List<PendingChunkSubmissionResponse> pendingSubmissions(
            UUID generationId, String afterChunkId) {
        IndexGenerationEntity generation = generationRepository.findById(
                generationId).orElseThrow(() -> new IllegalArgumentException(
                        "Generation not found: " + generationId));
        if (generation.getStatus() != IndexGenerationStatus.BUILDING
                || !generation.isVectorSubmissionStarted()) {
            return List.of();
        }

        return chunkRepository.findSubmissionPage(
                        generationId, ChunkIndexStatus.SUBMITTED,
                        afterChunkId == null ? "" : afterChunkId,
                        PageRequest.of(0, 100))
                .stream()
                .map(PendingChunkSubmissionResponse::from)
                .toList();
    }

    private final IndexGenerationRepository generationRepository;
    private final IndexedSourceFileRepository fileRepository;
    private final IndexedChunkRepository chunkRepository;
    private final DependencyGraphService dependencyGraphService;

        public IndexManifestService(
                IndexGenerationRepository generationRepository,
                IndexedSourceFileRepository fileRepository,
                IndexedChunkRepository chunkRepository,
                DependencyGraphService dependencyGraphService
        ) {
        this.generationRepository = generationRepository;
        this.fileRepository = fileRepository;
        this.chunkRepository = chunkRepository;
        this.dependencyGraphService =
                dependencyGraphService;
        }

    @Transactional
    public int storeFiles(
            UUID generationId,
            List<ManifestFileInput> inputs
    ) {
        requireBuildingGeneration(generationId);

        if (inputs == null || inputs.isEmpty()) {
            return 0;
        }

        int inserted = 0;

        for (ManifestFileInput input : inputs) {
            IndexedSourceFileEntity existing =
                    fileRepository
                            .findByGenerationIdAndPath(
                                    generationId,
                                    input.path()
                            )
                            .orElse(null);

            if (existing != null) {
                verifySameFile(existing, input);
                continue;
            }

            fileRepository.save(
                    IndexedSourceFileEntity.create(
                            generationId,
                            input.path(),
                            input.language(),
                            input.gitBlobSha(),
                            input.contentSha256(),
                            input.sizeBytes(),
                            input.content()
                    )
            );

            inserted++;
        }

        return inserted;
    }

    @Transactional
    public int storeChunks(
            UUID generationId,
            List<ManifestChunkInput> inputs
    ) {
        requireBuildingGeneration(generationId);

        if (inputs == null || inputs.isEmpty()) {
            return 0;
        }

        Map<String, IndexedSourceFileEntity> filesByPath =
                new HashMap<>();

        for (IndexedSourceFileEntity file :
                fileRepository
                        .findByGenerationIdOrderByPathAsc(
                                generationId
                        )) {
            filesByPath.put(file.getPath(), file);
        }

        int inserted = 0;

        for (ManifestChunkInput input : inputs) {
            IndexedSourceFileEntity file =
                    filesByPath.get(input.filePath());

            if (file == null) {
                throw new IllegalArgumentException(
                        "Manifest file does not exist: "
                                + input.filePath()
                );
            }

            if (!file.getContentSha256().equals(
                    input.fileContentSha256()
            )) {
                throw new IllegalArgumentException(
                        "Chunk file hash does not match "
                                + input.filePath()
                );
            }

            IndexedChunkEntity existingById =
                    chunkRepository
                            .findByGenerationIdAndChunkId(
                                    generationId,
                                    input.chunkId()
                            )
                            .orElse(null);

            if (existingById != null) {
                verifySameChunk(existingById, input);
                continue;
            }

            IndexedChunkEntity existingByLabel =
                    chunkRepository
                            .findByGenerationIdAndVectorLabel(
                                    generationId,
                                    input.vectorLabel()
                            )
                            .orElse(null);

            if (existingByLabel != null) {
                throw new IllegalStateException(
                        "Vector-label collision between chunks "
                                + existingByLabel.getChunkId()
                                + " and "
                                + input.chunkId()
                );
            }

            chunkRepository.save(
                    IndexedChunkEntity.pending(
                            generationId,
                            file.getFileId(),

                            input.chunkId(),
                            input.vectorLabelAsLong(),

                            input.path(),
                            input.language(),
                            input.symbol(),

                            input.startLine(),
                            input.endLine(),

                            input.fileContentSha256(),
                            input.chunkContentSha256(),
                            input.chunkerVersion(),

                            input.sourceContent(),
                            input.embeddingText()
                    )
            );

            inserted++;
        }

        return inserted;
    }



    @Transactional
        public int registerSubmissions(
                UUID generationId,
                List<ChunkSubmissionInput> inputs
        ) {
        requireBuildingGeneration(generationId);

        if (inputs == null || inputs.isEmpty()) {
                return 0;
        }

        int registered = 0;

        for (ChunkSubmissionInput input : inputs) {
                if (input == null) {
                throw new IllegalArgumentException(
                        "Submission entry is required"
                );
                }

                if (input.chunkId() == null
                        || input.chunkId().isBlank()) {
                throw new IllegalArgumentException(
                        "chunkId is required"
                );
                }

                if (input.submissionEventId() == null) {
                throw new IllegalArgumentException(
                        "submissionEventId is required"
                );
                }

                IndexedChunkEntity chunk =
                        chunkRepository
                                .findByGenerationIdAndChunkId(
                                        generationId,
                                        input.chunkId()
                                )
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "Manifest chunk does not exist: "
                                                        + input.chunkId()
                                        )
                                );

                IndexedChunkEntity existingSubmission =
                        chunkRepository
                                .findBySubmissionEventId(
                                        input.submissionEventId()
                                )
                                .orElse(null);

                if (existingSubmission != null
                        && !existingSubmission
                                .getChunkRowId()
                                .equals(chunk.getChunkRowId())) {
                throw new IllegalStateException(
                        "Submission event ID is already assigned "
                                + "to another chunk: "
                                + input.submissionEventId()
                );
                }

                if (chunk.markSubmitted(
                        input.submissionEventId()
                )) {
                registered++;
                }
        }

        return registered;
        }

    @Transactional
    public ManifestRegistrationResult completeManifest(
            UUID generationId,
            int expectedFiles,
            int expectedChunks
    ) {
        IndexGenerationEntity generation =
                requireBuildingGeneration(generationId);

        long storedFiles =
                fileRepository.countByGenerationId(
                        generationId
                );

        long storedChunks =
                chunkRepository.countByGenerationId(
                        generationId
                );

        if (storedFiles != expectedFiles) {
            throw new IllegalStateException(
                    "Expected "
                            + expectedFiles
                            + " files but stored "
                            + storedFiles
            );
        }

        if (storedChunks != expectedChunks) {
            throw new IllegalStateException(
                    "Expected "
                            + expectedChunks
                            + " chunks but stored "
                            + storedChunks
            );
        }

        if (expectedFiles <= 0 || expectedChunks <= 0) {
            throw new IllegalStateException(
                    "An empty manifest cannot be indexed"
            );
        }

        if (generation.getExpectedFiles() == 0
                && generation.getExpectedChunks() == 0) {
            generation.registerManifest(
                    expectedFiles,
                    expectedChunks
            );
        } else if (generation.getExpectedFiles()
                != expectedFiles
                || generation.getExpectedChunks()
                != expectedChunks) {
            throw new IllegalStateException(
                    "Manifest counts were already registered "
                            + "with different values"
            );
        }

        dependencyGraphService.rebuild(generationId);

        return new ManifestRegistrationResult(
                generationId,
                expectedFiles,
                expectedChunks,
                Math.toIntExact(storedFiles),
                Math.toIntExact(storedChunks),
                true
        );
    }

    private IndexGenerationEntity requireBuildingGeneration(
            UUID generationId
    ) {
        IndexGenerationEntity generation =
                generationRepository
                        .findLockedByGenerationId(generationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Generation not found: "
                                                + generationId
                                )
                        );

        if (generation.getStatus()
                != IndexGenerationStatus.BUILDING) {
            throw new IllegalStateException(
                    "Generation is not building: "
                            + generationId
            );
        }

        return generation;
    }

    private void verifySameFile(
            IndexedSourceFileEntity existing,
            ManifestFileInput input
    ) {
        boolean same =
                existing.getGitBlobSha().equals(
                        input.gitBlobSha()
                )
                && existing.getContentSha256().equals(
                        input.contentSha256()
                )
                && existing.getSizeBytes()
                        == input.sizeBytes()
                && existing.getContent().equals(
                        input.content()
                );

        if (!same) {
            throw new IllegalStateException(
                    "Conflicting manifest file: "
                            + input.path()
            );
        }
    }

    private void verifySameChunk(
            IndexedChunkEntity existing,
            ManifestChunkInput input
    ) {
        boolean same =
                existing.getVectorLabel().equals(
                        input.vectorLabel()
                )
                && existing.getPath().equals(
                        input.path()
                )
                && existing.getStartLine()
                        == input.startLine()
                && existing.getEndLine()
                        == input.endLine()
                && existing.getChunkContentSha256().equals(
                        input.chunkContentSha256()
                )
                && existing.getEmbeddingText().equals(
                        input.embeddingText()
                );

        if (!same) {
            throw new IllegalStateException(
                    "Conflicting manifest chunk: "
                            + input.chunkId()
            );
        }
    }
}


