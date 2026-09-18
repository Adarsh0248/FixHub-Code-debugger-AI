package com.razeef.bugbrother.indexes.controller;

import com.razeef.bugbrother.indexes.dto.request.CompleteManifestRequest;
import com.razeef.bugbrother.indexes.dto.request.ManifestChunkInput;
import com.razeef.bugbrother.indexes.dto.request.ManifestChunksRequest;
import com.razeef.bugbrother.indexes.dto.request.ManifestFileInput;
import com.razeef.bugbrother.indexes.dto.request.ManifestFilesRequest;
import com.razeef.bugbrother.indexes.dto.response.ManifestBatchResponse;
import com.razeef.bugbrother.indexes.dto.response.ManifestRegistrationResult;
import com.razeef.bugbrother.indexes.service.IndexManifestService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/index-generations")
public class InternalIndexManifestController {

    private static final int MAX_FILE_BATCH_SIZE = 20;
    private static final int MAX_CHUNK_BATCH_SIZE = 100;

    private final IndexManifestService manifestService;

    public InternalIndexManifestController(
            IndexManifestService manifestService
    ) {
        this.manifestService = manifestService;
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


