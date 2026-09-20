package com.razeef.bugbrother.retrieval.service;

import com.razeef.bugbrother.retrieval.config.ContextBudgetProperties;
import com.razeef.bugbrother.retrieval.exception.ContextBudgetExceededException;
import com.razeef.bugbrother.retrieval.model.ContextBundle;
import com.razeef.bugbrother.retrieval.model.ContextFile;
import com.razeef.bugbrother.retrieval.model.ContextFileRole;
import com.razeef.bugbrother.retrieval.model.DependencyExpansion;
import com.razeef.bugbrother.retrieval.model.ExpandedDependencyFile;
import com.razeef.bugbrother.retrieval.model.OmittedContextFile;
import com.razeef.bugbrother.retrieval.model.RetrievedChunk;
import com.razeef.bugbrother.retrieval.model.RetrievedSourceFile;
import com.razeef.bugbrother.retrieval.model.RequestedContextFile;
import com.razeef.bugbrother.retrieval.model.VectorContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ContextBundleService {

    private final ContextBudgetProperties budget;

    public ContextBundleService(ContextBudgetProperties budget) {
        this.budget = budget;
    }

    public ContextBundle build(
            VectorContext vectorContext,
            DependencyExpansion expansion,
            String errorQuery
    ) {
        return build(
                vectorContext,
                expansion,
                errorQuery,
                List.of()
        );
    }

    public ContextBundle build(
            VectorContext vectorContext,
            DependencyExpansion expansion,
            String errorQuery,
            List<RequestedContextFile> requestedFiles
    ) {
        validateInputs(vectorContext, expansion, errorQuery);
        if (requestedFiles == null) {
            throw new IllegalArgumentException(
                    "requestedFiles is required"
            );
        }

        List<ContextFile> primary = new ArrayList<>();
        List<ContextFile> supporting = new ArrayList<>();
        List<OmittedContextFile> omitted = new ArrayList<>();
        Set<UUID> includedIds = new HashSet<>();

        int usedCharacters = errorQuery.length();

        List<RequestedContextFile> rankedRequested = requestedFiles
                .stream()
                .sorted(Comparator
                        .comparingInt(RequestedContextFile::requestedRound)
                        .thenComparing(RequestedContextFile::path))
                .toList();

        for (RequestedContextFile file : rankedRequested) {
            if (includedIds.contains(file.fileId())) {
                continue;
            }

            String omission = omissionReason(
                    file.content(),
                    primary.size(),
                    budget.maxPrimaryFiles(),
                    usedCharacters,
                    "PRIMARY_FILE_LIMIT"
            );
            if (omission != null) {
                throw new ContextBudgetExceededException(
                        "Requested file cannot fit the context budget: "
                                + file.path()
                                + " ("
                                + omission
                                + ")"
                );
            }

            ContextFile contextFile = requestedFile(file);
            primary.add(contextFile);
            includedIds.add(file.fileId());
            usedCharacters += contextFile.characterCount();
        }

        List<RetrievedSourceFile> rankedPrimary = vectorContext
                .files()
                .stream()
                .sorted(Comparator
                        .comparingInt(RetrievedSourceFile::bestRank)
                        .thenComparing(RetrievedSourceFile::path))
                .toList();

        for (RetrievedSourceFile file : rankedPrimary) {
            if (includedIds.contains(file.fileId())) {
                continue;
            }
            String omission = omissionReason(
                    file.content(),
                    primary.size(),
                    budget.maxPrimaryFiles(),
                    usedCharacters,
                    "PRIMARY_FILE_LIMIT"
            );
            if (omission != null) {
                omitted.add(new OmittedContextFile(
                        file.fileId(),
                        file.path(),
                        ContextFileRole.PRIMARY,
                        omission
                ));
                continue;
            }

            ContextFile contextFile = primaryFile(file);
            primary.add(contextFile);
            includedIds.add(file.fileId());
            usedCharacters += contextFile.characterCount();
        }

        if (primary.isEmpty()) {
            throw new ContextBudgetExceededException(
                    "No complete primary file fits the context budget"
            );
        }

        List<ExpandedDependencyFile> rankedSupporting = expansion
                .files()
                .stream()
                .sorted(Comparator
                        .comparingInt(ExpandedDependencyFile::depth)
                        .thenComparing(ExpandedDependencyFile::path))
                .toList();

        for (ExpandedDependencyFile file : rankedSupporting) {
            if (includedIds.contains(file.fileId())) {
                continue;
            }

            String omission = omissionReason(
                    file.content(),
                    supporting.size(),
                    budget.maxSupportingFiles(),
                    usedCharacters,
                    "SUPPORTING_FILE_LIMIT"
            );
            if (omission != null) {
                omitted.add(new OmittedContextFile(
                        file.fileId(),
                        file.path(),
                        ContextFileRole.SUPPORTING,
                        omission
                ));
                continue;
            }

            ContextFile contextFile = supportingFile(file);
            supporting.add(contextFile);
            includedIds.add(file.fileId());
            usedCharacters += contextFile.characterCount();
        }

        return new ContextBundle(
                vectorContext.generationId(),
                vectorContext.commitSha(),
                errorQuery,
                List.copyOf(primary),
                List.copyOf(supporting),
                List.copyOf(omitted),
                usedCharacters,
                Math.max(1, (usedCharacters + 3) / 4)
        );
    }

    private String omissionReason(
            String content,
            int selectedCount,
            int fileLimit,
            int usedCharacters,
            String fileLimitReason
    ) {
        if (selectedCount >= fileLimit) {
            return fileLimitReason;
        }
        if (content == null) {
            return "MISSING_CONTENT";
        }
        if (content.length() > budget.maxSingleFileCharacters()) {
            return "SINGLE_FILE_LIMIT";
        }
        if (content.length()
                > budget.maxTotalCharacters() - usedCharacters) {
            return "TOTAL_CONTEXT_LIMIT";
        }
        return null;
    }

    private ContextFile primaryFile(RetrievedSourceFile file) {
        List<RetrievedChunk> matchedChunks =
                file.matchedChunks() == null
                        ? List.of()
                        : file.matchedChunks();

        String matchedRanges = matchedChunks
                .stream()
                .map(chunk -> chunk.startLine()
                        + "-"
                        + chunk.endLine())
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("unknown");

        return new ContextFile(
                file.fileId(),
                file.path(),
                file.language(),
                file.contentSha256(),
                file.content(),
                ContextFileRole.PRIMARY,
                "VECTOR_MATCH rank="
                        + file.bestRank()
                        + " distance="
                        + file.bestDistance()
                        + " lines="
                        + matchedRanges,
                file.bestRank(),
                file.content().length()
        );
    }

    private ContextFile supportingFile(
            ExpandedDependencyFile file
    ) {
        return new ContextFile(
                file.fileId(),
                file.path(),
                file.language(),
                file.contentSha256(),
                file.content(),
                ContextFileRole.SUPPORTING,
                file.relationship()
                        + " from "
                        + file.discoveredFromPath()
                        + " via "
                        + file.dependencyType()
                        + " ("
                        + file.evidence()
                        + ")",
                file.depth(),
                file.content().length()
        );
    }

    private ContextFile requestedFile(RequestedContextFile file) {
        return new ContextFile(
                file.fileId(),
                file.path(),
                file.language(),
                file.contentSha256(),
                file.content(),
                ContextFileRole.PRIMARY,
                "LLM_REQUEST round=" + file.requestedRound(),
                -file.requestedRound(),
                file.content().length()
        );
    }

    private void validateInputs(
            VectorContext vectorContext,
            DependencyExpansion expansion,
            String errorQuery
    ) {
        if (vectorContext == null || expansion == null) {
            throw new IllegalArgumentException(
                    "Vector context and dependency expansion are required"
            );
        }
        if (errorQuery == null || errorQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "errorQuery is required"
            );
        }
        if (!vectorContext.generationId()
                .equals(expansion.generationId())) {
            throw new IllegalArgumentException(
                    "Context sources belong to different generations"
            );
        }
        if (vectorContext.files() == null
                || vectorContext.files().isEmpty()) {
            throw new IllegalArgumentException(
                    "Vector context contains no files"
            );
        }
        if (expansion.files() == null) {
            throw new IllegalArgumentException(
                    "Dependency expansion files are required"
            );
        }
    }
}
