package com.razeef.bugbrother.dependencies.controller;

import com.razeef.bugbrother.dependencies.dto.request.ExpandDependenciesRequest;
import com.razeef.bugbrother.dependencies.dto.response.DependencyExpansionResponse;
import com.razeef.bugbrother.dependencies.service.DependencyExpansionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/index-generations")
public class InternalDependencyGraphController {

    private final DependencyExpansionService expansionService;

    public InternalDependencyGraphController(
            DependencyExpansionService expansionService
    ) {
        this.expansionService = expansionService;
    }

    @PostMapping("/{generationId}/expand-dependencies")
    public ResponseEntity<DependencyExpansionResponse> expand(
            @PathVariable UUID generationId,
            @RequestBody ExpandDependenciesRequest request
    ) {
        return ResponseEntity.ok(
                expansionService.expand(generationId, request)
        );
    }
}
