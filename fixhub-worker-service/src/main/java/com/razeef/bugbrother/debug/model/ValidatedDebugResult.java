package com.razeef.bugbrother.debug.model;

import java.util.List;

public record ValidatedDebugResult(
        List<ProposedFileChange> changes,
        String explanation
) {
}
