package com.razeef.bugbrother.dependencies.dto.response;

import java.util.List;
import java.util.UUID;

public record DependencyExpansionResponse(
        UUID generationId,
        int seedFiles,
        int expandedFiles,
        int traversedDepth,
        List<ExpandedDependencyFileResponse> files
) {
}
