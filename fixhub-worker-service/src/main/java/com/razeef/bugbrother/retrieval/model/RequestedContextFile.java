package com.razeef.bugbrother.retrieval.model;

import java.util.UUID;

public record RequestedContextFile(
        UUID fileId,
        String path,
        String language,
        String contentSha256,
        String content,
        int requestedRound
) {
    public static RequestedContextFile from(
            ResolvedSourceFile file,
            int requestedRound
    ) {
        return new RequestedContextFile(
                file.fileId(),
                file.path(),
                file.language(),
                file.contentSha256(),
                file.content(),
                requestedRound
        );
    }
}
