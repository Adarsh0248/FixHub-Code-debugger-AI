package com.razeef.bugbrother.indexes.dto.response;

import java.util.UUID;

public record ManifestBatchResponse(
        UUID generationId,
        int receivedItems,
        int insertedItems
) {
}


