package com.razeef.bugbrother.retrieval.model;

import java.util.List;
import java.util.UUID;

public record ContextBundle(
        UUID generationId,
        String commitSha,
        String errorQuery,
        List<ContextFile> primaryFiles,
        List<ContextFile> supportingFiles,
        List<OmittedContextFile> omittedFiles,
        int totalCharacters,
        int estimatedTokens
) {
}
