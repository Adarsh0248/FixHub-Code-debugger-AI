package com.razeef.bugbrother.retrieval.dto.request;

import java.util.List;

public record ResolveSourceFilesRequest(
        String vectorClientId,
        List<String> paths
) {
}
