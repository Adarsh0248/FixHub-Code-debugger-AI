package com.razeef.bugbrother.github.model;

public record GitTreeEntry(
        String path,
        String mode,
        String type,
        String sha
) {
}
