package com.razeef.bugbrother.retrieval.controller;

import com.razeef.bugbrother.retrieval.dto.request.ResolveVectorHitsRequest;
import com.razeef.bugbrother.retrieval.dto.request.ResolveSourceFilesRequest;
import com.razeef.bugbrother.retrieval.dto.response.ResolvedSourceFilesResponse;
import com.razeef.bugbrother.retrieval.dto.response.VectorContextResponse;
import com.razeef.bugbrother.retrieval.service.VectorContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/index-generations")
public class InternalVectorContextController {

    private final VectorContextService vectorContextService;

    public InternalVectorContextController(
            VectorContextService vectorContextService
    ) {
        this.vectorContextService = vectorContextService;
    }

    @PostMapping("/{generationId}/resolve-vector-hits")
    public ResponseEntity<VectorContextResponse> resolve(
            @PathVariable UUID generationId,
            @RequestBody ResolveVectorHitsRequest request
    ) {
        return ResponseEntity.ok(
                vectorContextService.resolve(
                        generationId,
                        request
                )
        );
    }

    @PostMapping("/{generationId}/resolve-files")
    public ResponseEntity<ResolvedSourceFilesResponse> resolveFiles(
            @PathVariable UUID generationId,
            @RequestBody ResolveSourceFilesRequest request
    ) {
        return ResponseEntity.ok(
                vectorContextService.resolveFiles(
                        generationId,
                        request
                )
        );
    }
}
