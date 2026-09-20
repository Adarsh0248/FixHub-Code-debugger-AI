package com.razeef.bugbrother.retrieval.model;

import java.util.UUID;

public record OmittedContextFile(
        UUID fileId,
        String path,
        ContextFileRole role,
        String reason
) {
}
