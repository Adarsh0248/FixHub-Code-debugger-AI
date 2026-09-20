package com.razeef.bugbrother.debug.model;

public record ProposedFileChange(
        String path,
        FileChangeOperation operation,
        String baseContentSha256,
        String content,
        String reason
) {
}
