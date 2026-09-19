package com.razeef.bugbrother.indexes.controller;

import com.razeef.bugbrother.indexes.dto.response.IndexGenerationStatusResponse;
import com.razeef.bugbrother.indexes.service.IndexGenerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/index-generations")
public class IndexGenerationController {

    private final IndexGenerationService generationService;

    public IndexGenerationController(
            IndexGenerationService generationService
    ) {
        this.generationService = generationService;
    }

    @GetMapping("/{generationId}")
    public ResponseEntity<IndexGenerationStatusResponse> get(
            @PathVariable UUID generationId
    ) {
        return ResponseEntity.ok(
                generationService.getStatus(generationId)
        );
    }
}
