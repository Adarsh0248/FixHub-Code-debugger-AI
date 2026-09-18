package com.razeef.bugbrother.indexes.model;

public enum IndexGenerationStatus {

    BUILDING,
    READY,
    FAILED,
    RETIRED;

    public boolean isTerminal() {
        return this == READY
                || this == FAILED
                || this == RETIRED;
    }

    public boolean canReceiveAcknowledgements() {
        return this == BUILDING;
    }
}


