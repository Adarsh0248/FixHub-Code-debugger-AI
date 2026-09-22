package com.razeef.bugbrother.debug.service;

import com.razeef.bugbrother.debug.ai.GitAiLayer;
import com.razeef.bugbrother.debug.model.DebugMode;
import com.razeef.bugbrother.debug.model.ModelGenerationResult;
import com.razeef.bugbrother.debug.model.StructuredDebugResponse;
import com.razeef.bugbrother.debug.parser.StructuredDebugResponseParser;
import com.razeef.bugbrother.debug.validation.RepositoryPathPolicy;
import com.razeef.bugbrother.debug.exception.ModelResponseValidationException;
import com.razeef.bugbrother.retrieval.client.IndexContextClient;
import com.razeef.bugbrother.retrieval.config.ContextBudgetProperties;
import com.razeef.bugbrother.retrieval.exception.ContextExpansionException;
import com.razeef.bugbrother.retrieval.exception.ContextRetrievalException;
import com.razeef.bugbrother.retrieval.model.ContextBundle;
import com.razeef.bugbrother.retrieval.model.DependencyExpansion;
import com.razeef.bugbrother.retrieval.model.RequestedContextFile;
import com.razeef.bugbrother.retrieval.model.ResolvedSourceFiles;
import com.razeef.bugbrother.retrieval.model.VectorContext;
import com.razeef.bugbrother.retrieval.service.ContextBundleService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IterativeModelGenerationService {

    private final ContextBundleService contextBundleService;
    private final IndexContextClient indexContextClient;
    private final StructuredDebugResponseParser responseParser;
    private final RepositoryPathPolicy pathPolicy;
    private final GitAiLayer gitAiLayer;
    private final ContextBudgetProperties budget;

    public IterativeModelGenerationService(
            ContextBundleService contextBundleService,
            IndexContextClient indexContextClient,
            StructuredDebugResponseParser responseParser,
            RepositoryPathPolicy pathPolicy,
            GitAiLayer gitAiLayer,
            ContextBudgetProperties budget
    ) {
        this.contextBundleService = contextBundleService;
        this.indexContextClient = indexContextClient;
        this.responseParser = responseParser;
        this.pathPolicy = pathPolicy;
        this.gitAiLayer = gitAiLayer;
        this.budget = budget;
    }

    public ModelGenerationResult generate(
            DebugMode mode,
            UUID generationId,
            String vectorClientId,
            VectorContext vectorContext,
            DependencyExpansion dependencyExpansion,
            String errorQuery
    ) {
        if (mode == null) {
            throw new IllegalArgumentException("Debug mode is required");
        }

        Map<String, RequestedContextFile> requestedByPath =
                new LinkedHashMap<>();

        for (int round = 0;
             round <= budget.maxExpansionRounds();
             round++) {
            ContextBundle bundle = contextBundleService.build(
                    vectorContext,
                    dependencyExpansion,
                    errorQuery,
                    new ArrayList<>(requestedByPath.values())
            );

            String rawResponse = mode == DebugMode.GUIDE_ONLY
                    ? gitAiLayer.askAiGuide(bundle)
                    : gitAiLayer.askAiDebug(bundle);

            StructuredDebugResponse response =
                    responseParser.parse(rawResponse);

            List<String> requestedPaths = normalizeRequests(
                    response.additionalContextRequests()
            );
            if (mode == DebugMode.GUIDE_ONLY
                    && !response.changes().isEmpty()) {
                throw new ModelResponseValidationException(
                        "Guide mode cannot return repository changes"
                );
            }
            if (!requestedPaths.isEmpty()
                    && !response.changes().isEmpty()) {
                throw new ModelResponseValidationException(
                        "A context request cannot include proposed changes"
                );
            }
            if (requestedPaths.isEmpty()) {
                if (mode == DebugMode.GUIDE_ONLY
                        && !hasConcreteExplanation(response)) {
                    response = responseParser.parse(
                            gitAiLayer.askAiGuideWithConcreteExplanation(bundle)
                    );
                    if (!response.changes().isEmpty()
                            || !normalizeRequests(
                            response.additionalContextRequests()).isEmpty()
                            || !hasConcreteExplanation(response)) {
                        throw new ModelResponseValidationException(
                                "The model did not provide concrete debugging guidance"
                        );
                    }
                }
                return new ModelGenerationResult(
                        response,
                        bundle,
                        round + 1
                );
            }

            if (round >= budget.maxExpansionRounds()) {
                throw new ContextExpansionException(
                        "The model exceeded the context expansion round limit"
                );
            }

            Set<String> primaryPaths = bundle.primaryFiles()
                    .stream()
                    .map(file -> file.path())
                    .collect(Collectors.toSet());

            List<String> newPaths = requestedPaths.stream()
                    .filter(path -> !primaryPaths.contains(path))
                    .filter(path -> !requestedByPath.containsKey(path))
                    .toList();

            if (newPaths.isEmpty()) {
                if (mode == DebugMode.GUIDE_ONLY) {
                    return finishGuideWithAvailableContext(bundle, round + 2);
                }
                throw new ContextExpansionException(
                        "The model repeatedly requested files already in primary context"
                );
            }

            if (requestedByPath.size() + newPaths.size()
                    > budget.maxRequestedFiles()) {
                throw new ContextExpansionException(
                        "The model exceeded the additional-file limit"
                );
            }

            ResolvedSourceFiles resolved;
            try {
                resolved = indexContextClient.resolveFiles(
                        generationId,
                        vectorClientId,
                        newPaths
                );
            } catch (ContextRetrievalException exception) {
                if (mode != DebugMode.GUIDE_ONLY
                        || !(exception.getCause()
                        instanceof WebClientResponseException.BadRequest)) {
                    throw exception;
                }

                return finishGuideWithAvailableContext(bundle, round + 2);
            }
            validateResolvedFiles(
                    resolved,
                    generationId,
                    vectorClientId,
                    vectorContext.commitSha(),
                    newPaths
            );

            int requestedRound = round + 1;
            resolved.files().forEach(file -> requestedByPath.put(
                    file.path(),
                    RequestedContextFile.from(file, requestedRound)
            ));
        }

        throw new ContextExpansionException(
                "Context expansion ended without a final model response"
        );
    }

    private List<String> normalizeRequests(List<String> requests) {
        if (requests == null) {
            throw new ModelResponseValidationException(
                    "The model response must include additionalContextRequests"
            );
        }

        return requests.stream()
                .map(pathPolicy::normalize)
                .distinct()
                .toList();
    }

    private boolean hasConcreteExplanation(StructuredDebugResponse response) {
        return response.explanation() != null
                && response.explanation().trim().length() >= 100
                && !response.explanation().toLowerCase()
                .contains("detailed developer guidance");
    }

    private ModelGenerationResult finishGuideWithAvailableContext(
            ContextBundle bundle,
            int rounds
    ) {
        StructuredDebugResponse fallback = responseParser.parse(
                gitAiLayer.askAiGuideWithConcreteExplanation(bundle)
        );
        if (!fallback.changes().isEmpty()
                || !normalizeRequests(
                fallback.additionalContextRequests()).isEmpty()
                || !hasConcreteExplanation(fallback)) {
            throw new ModelResponseValidationException(
                    "The model could not finish guidance with available files"
            );
        }
        return new ModelGenerationResult(fallback, bundle, rounds);
    }

    private void validateResolvedFiles(
            ResolvedSourceFiles resolved,
            UUID generationId,
            String vectorClientId,
            String commitSha,
            List<String> requestedPaths
    ) {
        if (resolved == null
                || !generationId.equals(resolved.generationId())
                || !vectorClientId.equals(resolved.vectorClientId())
                || !commitSha.equals(resolved.commitSha())) {
            throw new ContextExpansionException(
                    "Requested files belong to a different index generation"
            );
        }

        Set<String> returnedPaths = resolved.files()
                .stream()
                .map(file -> file.path())
                .collect(Collectors.toSet());

        if (!returnedPaths.equals(Set.copyOf(requestedPaths))) {
            throw new ContextExpansionException(
                    "Ingestion returned a different requested-file set"
            );
        }
    }
}
