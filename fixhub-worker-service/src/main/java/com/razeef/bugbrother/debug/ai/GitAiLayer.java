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

                Analyze the error across all provided context. Return corrected contents only
                for PRIMARY FILES that actually need a change. Never return a SUPPORTING FILE
                as corrected code in this response. If a supporting file also needs a change,
                state its exact path after all code blocks under a line beginning with:
                REQUIRED_ADDITIONAL_FILE:

                For each corrected primary file, use exactly this format:

                ==== File: <repository-relative path> ====
                ```<language>
                <complete corrected file contents>
                ```

                Do not put explanations inside code blocks. Do not invent files or paths.
                Preserve existing structure and behavior unless the reported fix requires a
                change. After all code blocks, explain the cause and the fix under the exact
                heading EXPLANATION:.
                """;
    }

    private String guideSystemPrompt() {
        return """
                You are an expert software engineer guiding a developer through a reported
                repository error. Analyze the complete source files and the error query.

                Explain:
                1. The likely root cause.
                2. Which repository files and symbols are involved.
                3. The changes the developer should make, in order.
                4. Small focused code examples where useful.
                5. How to verify the fix.

                Do not claim that you changed, committed, or pushed any file. Do not output
                complete replacement files unless the user explicitly needs one to understand
                the solution. If more repository context is required, write its exact path after
                REQUIRED_ADDITIONAL_FILE:.
                """;
    }
}
