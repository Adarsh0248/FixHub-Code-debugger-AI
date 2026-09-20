package com.razeef.bugbrother.retrieval.model;

import java.util.List;
import java.util.UUID;

public record ResolvedSourceFiles(
        UUID generationId,
        String vectorClientId,
        String commitSha,
        List<ResolvedSourceFile> files
) {
}
