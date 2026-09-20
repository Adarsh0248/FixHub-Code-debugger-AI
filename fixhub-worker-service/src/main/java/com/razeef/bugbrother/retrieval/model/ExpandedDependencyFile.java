package com.razeef.bugbrother.retrieval.model;

import java.util.UUID;

public record ExpandedDependencyFile(
        UUID fileId,
        String path,
        String language,
        String gitBlobSha,
        String contentSha256,
        String content,
        int depth,
        UUID discoveredFromFileId,
        String discoveredFromPath,
        String relationship,
        String dependencyType,
        String evidence
) {
}
