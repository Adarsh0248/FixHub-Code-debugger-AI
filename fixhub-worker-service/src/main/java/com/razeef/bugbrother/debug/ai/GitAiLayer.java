package com.razeef.bugbrother.debug.ai;

import com.razeef.bugbrother.github.service.CommitService;
import com.razeef.bugbrother.retrieval.model.ContextBundle;
import com.razeef.bugbrother.retrieval.model.ContextFile;
import com.razeef.bugbrother.retrieval.model.OmittedContextFile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GitAiLayer {

    private final AiService aiService;

    public GitAiLayer(AiService aiService) {
        this.aiService = aiService;
    }

    public String askAiDebug(ContextBundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("Context bundle is required");
        }

        StringBuilder request = new StringBuilder();
        request.append("Error query:\n")
                .append(bundle.errorQuery())
                .append("\n\nBase commit: ")
                .append(bundle.commitSha())
                .append("\n\n");

        for (ContextFile file : bundle.primaryFiles()) {
            appendContextFile(request, file, "PRIMARY FILE (EDITABLE CANDIDATE)");
        }

        for (ContextFile file : bundle.supportingFiles()) {
            appendContextFile(request, file, "SUPPORTING FILE (READ ONLY)");
        }

        if (!bundle.omittedFiles().isEmpty()) {
            request.append("==== OMITTED CONTEXT ====\n");
            for (OmittedContextFile file : bundle.omittedFiles()) {
                request.append("- ")
                        .append(file.path())
                        .append(" [")
                        .append(file.role())
                        .append("]: ")
                        .append(file.reason())
                        .append('\n');
            }
            request.append('\n');
        }

        return aiService.chatWithSystem(systemPrompt(), request.toString());
    }

    public String askAiGuide(ContextBundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("Context bundle is required");
        }

        StringBuilder request = new StringBuilder();
        request.append("Error query:\n")
                .append(bundle.errorQuery())
                .append("\n\nBase commit: ")
                .append(bundle.commitSha())
                .append("\n\n");

        for (ContextFile file : bundle.primaryFiles()) {
            appendContextFile(request, file, "PRIMARY FILE");
        }

        for (ContextFile file : bundle.supportingFiles()) {
            appendContextFile(request, file, "SUPPORTING FILE");
        }

        return aiService.chatWithSystem(guideSystemPrompt(), request.toString());
    }

    /** Kept temporarily for callers that do not yet build a ContextBundle. */
    public String askAiDebug(
            List<CommitService.FixedFile> dataList,
            String userQuery
    ) {
        return askAiDebug(dataList, userQuery, List.of());
    }

    /** Kept temporarily for callers that do not yet build a ContextBundle. */
    public String askAiDebug(
            List<CommitService.FixedFile> dataList,
            String userQuery,
            List<CommitService.FixedFile> relatedContext
    ) {
        StringBuilder request = new StringBuilder();
        request.append("Error query:\n").append(userQuery).append("\n\n");

        for (CommitService.FixedFile file : dataList) {
            request.append("==== PRIMARY FILE (EDITABLE CANDIDATE): ")
                    .append(file.path())
                    .append(" ====\n")
                    .append(file.fixedContent())
                    .append("\n\n");
        }

        for (CommitService.FixedFile file : relatedContext) {
            request.append("==== SUPPORTING FILE (READ ONLY): ")
                    .append(file.path())
                    .append(" ====\n")
                    .append(file.fixedContent())
                    .append("\n\n");
        }

        return aiService.chatWithSystem(systemPrompt(), request.toString());
    }

    private void appendContextFile(
            StringBuilder request,
            ContextFile file,
            String heading
    ) {
        request.append("==== ")
                .append(heading)
                .append(": ")
                .append(file.path())
                .append(" ====\n")
                .append("Selection reason: ")
                .append(file.selectionReason())
                .append("\nLanguage: ")
                .append(file.language())
                .append("\nContent SHA-256: ")
                .append(file.contentSha256())
                .append("\n\n")
                .append(file.content())
                .append("\n\n");
    }

    private String systemPrompt() {
        return """
                You are an expert software engineer fixing a reported error in a repository.

                The request contains two kinds of complete source files:
                - PRIMARY FILES are the current editable candidates selected by vector search.
                - SUPPORTING FILES are read-only dependency context used to understand types,
                  imports, contracts, and repository conventions.

                Return exactly one JSON object with no markdown fence and no surrounding text:
                {
                  "changes": [
                    {
                      "path": "repository-relative path",
                      "operation": "UPDATE",
                      "baseContentSha256": "SHA-256 shown with the primary file",
                      "content": "complete corrected file content",
                      "reason": "why this file must change"
                    }
                  ],
                  "additionalContextRequests": ["exact/repository/path"],
                  "explanation": "cause of the error and how the changes fix it"
                }

                Only PRIMARY FILES may appear in changes. Never change a SUPPORTING FILE. If a
                supporting or missing file must become editable, put its exact path in
                additionalContextRequests and return an empty changes array for this round.
                Do not invent paths. Use UPDATE only. Include complete file contents, preserve
                repository structure, and copy the exact baseContentSha256 supplied with the
                primary file. Use an empty additionalContextRequests array when context is
                sufficient.
                """;
    }

    private String guideSystemPrompt() {
        return """
                You are an expert software engineer guiding a developer through a reported
                repository error. Analyze the complete source files and the error query.

                Return exactly one JSON object with no markdown fence and no surrounding text:
                {
                  "changes": [],
                  "additionalContextRequests": ["exact/repository/path"],
                  "explanation": "detailed developer guidance"
                }

                The changes array must always be empty. In explanation, describe the root cause,
                involved files and symbols, ordered solution steps, focused code examples where
                useful, and verification steps. Do not claim that files were changed, committed,
                or pushed. If more context is required, request only exact repository paths in
                additionalContextRequests. Otherwise use an empty array.
                """;
    }
}
