package com.razeef.bugbrother.retrieval.model;

import java.util.UUID;

public record ContextFile(
        UUID fileId,
        String path,
        String language,
        String contentSha256,
        String content,
        ContextFileRole role,
        String selectionReason,
        int priority,
        int characterCount
) {
}
