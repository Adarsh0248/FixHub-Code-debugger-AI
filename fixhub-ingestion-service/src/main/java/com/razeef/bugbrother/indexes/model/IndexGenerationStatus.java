package com.razeef.bugbrother.indexes.model;

public enum IndexGenerationStatus {

    BUILDING,
    READY,
    FAILED,
    CLEANING,
    RETIRED;

    public boolean isTerminal() {
        return this == READY
                || this == FAILED
                || this == CLEANING
                || this == RETIRED;
    }

    public boolean canReceiveAcknowledgements() {
        return this == BUILDING;
    }
}


