package com.razeef.bugbrother.debug.model;

import java.util.List;

public record StructuredDebugResponse(
        List<ProposedFileChange> changes,
        List<String> additionalContextRequests,
        String explanation
) {
}
