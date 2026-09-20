package com.razeef.bugbrother.dependencies.service;

import com.razeef.bugbrother.dependencies.dto.request.ExpandDependenciesRequest;
import com.razeef.bugbrother.dependencies.dto.response.DependencyExpansionResponse;
import com.razeef.bugbrother.dependencies.dto.response.ExpandedDependencyFileResponse;
import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;
import com.razeef.bugbrother.indexes.model.IndexedSourceFileEntity;
import com.razeef.bugbrother.indexes.model.SourceFileDependencyEntity;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;
import com.razeef.bugbrother.indexes.repository.IndexedSourceFileRepository;
import com.razeef.bugbrother.indexes.repository.SourceFileDependencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DependencyExpansionService {

    private static final int MAX_ALLOWED_DEPTH = 3;
    private static final int MAX_ALLOWED_FILES = 30;
    private static final int MAX_SEED_FILES = 20;

    private final IndexGenerationRepository generationRepository;
    private final IndexedSourceFileRepository fileRepository;
    private final SourceFileDependencyRepository dependencyRepository;

    public DependencyExpansionService(
            IndexGenerationRepository generationRepository,
            IndexedSourceFileRepository fileRepository,
            SourceFileDependencyRepository dependencyRepository
    ) {
        this.generationRepository = generationRepository;
        this.fileRepository = fileRepository;
        this.dependencyRepository = dependencyRepository;
    }

    @Transactional(readOnly = true)
    public DependencyExpansionResponse expand(
            UUID generationId,
            ExpandDependenciesRequest request
    ) {
        validateRequest(generationId, request);

        IndexGenerationEntity generation = generationRepository
                .findById(generationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Generation not found: " + generationId
                ));

        validateGeneration(generation, request);

        List<UUID> seeds = List.copyOf(
                new LinkedHashSet<>(request.seedFileIds())
        );
        validateSeeds(generationId, seeds);

        Set<UUID> visited = new LinkedHashSet<>(seeds);
        List<UUID> frontier = new ArrayList<>(seeds);
        Map<UUID, Discovery> discoveries = new LinkedHashMap<>();
        int traversedDepth = 0;

        for (int depth = 1;
             depth <= request.maxDepth()
                     && !frontier.isEmpty()
                     && discoveries.size() < request.maxFiles();
             depth++) {
            List<Candidate> candidates = findCandidates(
                    generationId,
                    frontier
            );
            List<UUID> nextFrontier = new ArrayList<>();

            for (Candidate candidate : candidates) {
                if (discoveries.size() >= request.maxFiles()) {
                    break;
                }
                if (!visited.add(candidate.fileId())) {
                    continue;
                }

                discoveries.put(
                        candidate.fileId(),
                        new Discovery(
                                candidate.fileId(),
                                candidate.discoveredFromFileId(),
                                candidate.relationship(),
                                candidate.edge(),
                                depth
                        )
                );
                nextFrontier.add(candidate.fileId());
            }

            if (!nextFrontier.isEmpty()) {
                traversedDepth = depth;
            }
            frontier = nextFrontier;
        }

        Set<UUID> requiredFileIds = new LinkedHashSet<>(seeds);
        requiredFileIds.addAll(discoveries.keySet());
        discoveries.values().stream()
                .map(Discovery::discoveredFromFileId)
                .forEach(requiredFileIds::add);

        Map<UUID, IndexedSourceFileEntity> filesById = new HashMap<>();
        for (IndexedSourceFileEntity file : fileRepository
                .findByGenerationIdAndFileIdIn(
                        generationId,
                        requiredFileIds
                )) {
            filesById.put(file.getFileId(), file);
        }

        List<ExpandedDependencyFileResponse> files = new ArrayList<>();
        for (Discovery discovery : discoveries.values()) {
            IndexedSourceFileEntity file = filesById.get(
                    discovery.fileId()
            );
            IndexedSourceFileEntity parent = filesById.get(
                    discovery.discoveredFromFileId()
            );

            if (file == null || parent == null) {
                throw new IllegalStateException(
                        "Dependency graph points to a missing file"
                );
            }

            files.add(new ExpandedDependencyFileResponse(
                    file.getFileId(),
                    file.getPath(),
                    file.getLanguage(),
                    file.getGitBlobSha(),
                    file.getContentSha256(),
                    file.getContent(),
                    discovery.depth(),
                    parent.getFileId(),
                    parent.getPath(),
                    discovery.relationship(),
                    discovery.edge().getDependencyType().name(),
                    discovery.edge().getEvidence()
            ));
        }

        return new DependencyExpansionResponse(
                generationId,
                seeds.size(),
                files.size(),
                traversedDepth,
                List.copyOf(files)
        );
    }

    private List<Candidate> findCandidates(
            UUID generationId,
            List<UUID> frontier
    ) {
        List<Candidate> candidates = new ArrayList<>();

        List<SourceFileDependencyEntity> outgoing = dependencyRepository
                .findByGenerationIdAndSourceFileIdIn(
                        generationId,
                        frontier
                );
        outgoing.sort(edgeComparator());
        for (SourceFileDependencyEntity edge : outgoing) {
            candidates.add(new Candidate(
                    edge.getTargetFileId(),
                    edge.getSourceFileId(),
                    "DEPENDS_ON",
                    edge
            ));
        }

        List<SourceFileDependencyEntity> incoming = dependencyRepository
                .findByGenerationIdAndTargetFileIdIn(
                        generationId,
                        frontier
                );
        incoming.sort(edgeComparator());
        for (SourceFileDependencyEntity edge : incoming) {
            candidates.add(new Candidate(
                    edge.getSourceFileId(),
                    edge.getTargetFileId(),
                    "DEPENDENT_OF",
                    edge
            ));
        }

        return candidates;
    }

    private Comparator<SourceFileDependencyEntity> edgeComparator() {
        return Comparator
                .comparing((SourceFileDependencyEntity edge) ->
                        edge.getSourceFileId().toString())
                .thenComparing(edge ->
                        edge.getTargetFileId().toString())
                .thenComparing(edge ->
                        edge.getDependencyType().name());
    }

    private void validateSeeds(
            UUID generationId,
            List<UUID> seeds
    ) {
        List<IndexedSourceFileEntity> files = fileRepository
                .findByGenerationIdAndFileIdIn(
                        generationId,
                        seeds
                );
        if (files.size() != seeds.size()) {
            throw new IllegalArgumentException(
                    "One or more seed files do not belong "
                            + "to the requested generation"
            );
        }
    }

    private void validateRequest(
            UUID generationId,
            ExpandDependenciesRequest request
    ) {
        if (generationId == null) {
            throw new IllegalArgumentException(
                    "generationId is required"
            );
        }
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dependency expansion request is required"
            );
        }
        if (request.vectorClientId() == null
                || request.vectorClientId().isBlank()) {
            throw new IllegalArgumentException(
                    "vectorClientId is required"
            );
        }
        if (request.seedFileIds() == null
                || request.seedFileIds().isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one seed file is required"
            );
        }
        if (request.seedFileIds().size() > MAX_SEED_FILES) {
            throw new IllegalArgumentException(
                    "Dependency expansion cannot exceed "
                            + MAX_SEED_FILES
                            + " seed files"
            );
        }
        if (request.seedFileIds().stream()
                .anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Seed file IDs cannot contain null"
            );
        }
        if (request.maxDepth() < 1
                || request.maxDepth() > MAX_ALLOWED_DEPTH) {
            throw new IllegalArgumentException(
                    "maxDepth must be between 1 and "
                            + MAX_ALLOWED_DEPTH
            );
        }
        if (request.maxFiles() < 1
                || request.maxFiles() > MAX_ALLOWED_FILES) {
            throw new IllegalArgumentException(
                    "maxFiles must be between 1 and "
                            + MAX_ALLOWED_FILES
            );
        }
    }

    private void validateGeneration(
            IndexGenerationEntity generation,
            ExpandDependenciesRequest request
    ) {
        if (generation.getStatus() != IndexGenerationStatus.READY) {
            throw new IllegalStateException(
                    "Only a ready generation can be expanded"
            );
        }
        if (!generation.getVectorClientId()
                .equals(request.vectorClientId())) {
            throw new IllegalArgumentException(
                    "Vector client ID does not match generation"
            );
        }
    }

    private record Candidate(
            UUID fileId,
            UUID discoveredFromFileId,
            String relationship,
            SourceFileDependencyEntity edge
    ) {
    }

    private record Discovery(
            UUID fileId,
            UUID discoveredFromFileId,
            String relationship,
            SourceFileDependencyEntity edge,
            int depth
    ) {
    }
}
