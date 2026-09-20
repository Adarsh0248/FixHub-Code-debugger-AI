package com.razeef.bugbrother.dependencies.model;

import java.util.UUID;

public record DependencyGraphBuildResult(
        UUID generationId,
        int inspectedFiles,
        int declaredSymbols,
        int dependencyEdges
) {
}