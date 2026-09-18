package com.razeef.bugbrother.indexes.model;

public enum ChunkIndexStatus {

    PENDING,
    SUBMITTED,
    INDEXED,
    FAILED;

    public boolean isTerminal() {
        return this == INDEXED
                || this == FAILED;
    }
}


