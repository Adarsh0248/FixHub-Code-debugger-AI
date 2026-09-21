package com.razeef.bugbrother.indexes.controller;

import com.razeef.bugbrother.indexes.dto.request.CompleteManifestRequest;
import com.razeef.bugbrother.indexes.dto.request.ManifestChunkInput;
import com.razeef.bugbrother.indexes.dto.request.ManifestChunksRequest;
import com.razeef.bugbrother.indexes.dto.request.ManifestFileInput;
import com.razeef.bugbrother.indexes.dto.request.ManifestFilesRequest;
import com.razeef.bugbrother.indexes.dto.response.ManifestBatchResponse;
import com.razeef.bugbrother.indexes.dto.response.ManifestRegistrationResult;
import com.razeef.bugbrother.indexes.service.IndexManifestService;
import com.razeef.bugbrother.indexes.dto.request.FailIndexGenerationRequest;
import com.razeef.bugbrother.indexes.service.IndexGenerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.razeef.bugbrother.indexes.dto.request.ChunkSubmissionInput;
import com.razeef.bugbrother.indexes.dto.request.ChunkSubmissionsRequest;
import java.util.List;
import java.util.UUID;
import com.razeef.bugbrother.indexes.dto.response.IndexCleanupPlanResponse;
import com.razeef.bugbrother.indexes.dto.response.IndexSubmissionRecoveryState;
import com.razeef.bugbrother.indexes.dto.response.PendingChunkSubmissionResponse;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/internal/index-generations")
public class InternalIndexManifestController {

    @GetMapping("/{generationId}/recovery-state")
    public ResponseEntity<IndexSubmissionRecoveryState> recoveryState(
            @PathVariable UUID generationId) {
        return ResponseEntity.ok(manifestService.recoveryState(generationId));
    }

    @GetMapping("/{generationId}/pending-submissions")
    public ResponseEntity<List<PendingChunkSubmissionResponse>> pendingSubmissions(
            @PathVariable UUID generationId,
            @RequestParam(defaultValue = "") String afterChunkId) {
        return ResponseEntity.ok(manifestService.pendingSubmissions(
                generationId, afterChunkId));
    }

    private static final int MAX_FILE_BATCH_SIZE = 20;
    private static final int MAX_CHUNK_BATCH_SIZE = 100;
    private static final int MAX_SUBMISSION_BATCH_SIZE = 100;
    private final IndexGenerationService generationService;
    private final IndexManifestService manifestService;

    public InternalIndexManifestController(
        IndexManifestService manifestService,
        IndexGenerationService generationService
        ) {
        this.manifestService = manifestService;
        this.generationService = generationService;
        }

    @PostMapping("/{generationId}/manifest/files")
    public ResponseEntity<ManifestBatchResponse> storeFiles(
            @PathVariable UUID generationId,
            @RequestBody ManifestFilesRequest request
    ) {
        List<ManifestFileInput> files =
                request == null || request.files() == null
                        ? List.of()
                        : request.files();

        requireMaximumSize(
                files.size(),
                MAX_FILE_BATCH_SIZE,
                "file"
        );

        int inserted = manifestService.storeFiles(
                generationId,
                files
        );

        return ResponseEntity.ok(
                new ManifestBatchResponse(
                        generationId,
                        files.size(),
                        inserted
                )
        );
    }

    @PostMapping("/{generationId}/manifest/chunks")
    public ResponseEntity<ManifestBatchResponse> storeChunks(
            @PathVariable UUID generationId,
            @RequestBody ManifestChunksRequest request
    ) {
        List<ManifestChunkInput> chunks =
                request == null || request.chunks() == null
                        ? List.of()
                        : request.chunks();

        requireMaximumSize(
                chunks.size(),
                MAX_CHUNK_BATCH_SIZE,
                "chunk"
        );

        int inserted = manifestService.storeChunks(
                generationId,
                chunks
        );

        return ResponseEntity.ok(
                new ManifestBatchResponse(
                        generationId,
                        chunks.size(),
                        inserted
                )
        );
    }


    @PostMapping("/{generationId}/submissions")
        public ResponseEntity<ManifestBatchResponse>
        registerSubmissions(
                @PathVariable UUID generationId,
                @RequestBody ChunkSubmissionsRequest request
        ) {
        List<ChunkSubmissionInput> submissions =
                request == null
                        || request.submissions() == null
                        ? List.of()
                        : request.submissions();

        requireMaximumSize(
                submissions.size(),
                MAX_SUBMISSION_BATCH_SIZE,
                "submission"
        );

        int registered =
                manifestService.registerSubmissions(
                        generationId,
                        submissions
                );

        return ResponseEntity.ok(
                new ManifestBatchResponse(
                        generationId,
                        submissions.size(),
                        registered
                )
        );
        }

    @PostMapping("/{generationId}/manifest/complete")
    public ResponseEntity<ManifestRegistrationResult>
    completeManifest(
            @PathVariable UUID generationId,
            @RequestBody CompleteManifestRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Manifest completion body is required"
            );
        }

        return ResponseEntity.ok(
                manifestService.completeManifest(
                        generationId,
                        request.expectedFiles(),
                        request.expectedChunks()
                )
        );
    }


    @PostMapping("/{generationId}/fail")
        public ResponseEntity<Void> failGeneration(
                @PathVariable UUID generationId,
                @RequestBody FailIndexGenerationRequest request
        ) {
        if (request == null) {
                throw new IllegalArgumentException(
                        "Generation failure body is required"
                );
        }

        generationService.failGeneration(
                generationId,
                request.errorCode(),
                request.errorMessage(),
                request.vectorsMayExist()
        );

        return ResponseEntity.noContent().build();
        }

    @PostMapping("/{generationId}/discard-prepared")
    public ResponseEntity<Void> discardPrepared(
            @PathVariable UUID generationId
    ) {
        generationService.discardPreparedGeneration(generationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{generationId}/submission-started")
    public ResponseEntity<Void> markSubmissionStarted(
            @PathVariable UUID generationId
    ) {
        generationService.markVectorSubmissionStarted(generationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{generationId}/cleanup-plan")
    public ResponseEntity<IndexCleanupPlanResponse> cleanupPlan(
            @PathVariable UUID generationId
    ) {
        return generationService.cleanupPlan(generationId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{generationId}/cleanup-complete")
    public ResponseEntity<Void> completeCleanup(
            @PathVariable UUID generationId
    ) {
        generationService.completeCleanup(generationId);
        return ResponseEntity.noContent().build();
    }

    private void requireMaximumSize(
            int actualSize,
            int maximumSize,
            String itemType
    ) {
        if (actualSize > maximumSize) {
            throw new IllegalArgumentException(
                    "A "
                            + itemType
                            + " batch cannot exceed "
                            + maximumSize
                            + " items"
            );
        }
    }
}


