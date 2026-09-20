package com.razeef.bugbrother.retrieval.model;

import java.util.List;
import java.util.UUID;

public record DependencyExpansion(
        UUID generationId,
        int seedFiles,
        int expandedFiles,
        int traversedDepth,
        List<ExpandedDependencyFile> files
) {
}
