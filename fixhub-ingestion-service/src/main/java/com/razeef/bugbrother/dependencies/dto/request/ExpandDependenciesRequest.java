package com.razeef.bugbrother.dependencies.dto.request;

import java.util.List;
import java.util.UUID;

public record ExpandDependenciesRequest(
        String vectorClientId,
        List<UUID> seedFileIds,
        int maxDepth,
        int maxFiles
) {
}
