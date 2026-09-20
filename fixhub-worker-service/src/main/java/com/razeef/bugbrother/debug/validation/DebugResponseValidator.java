package com.razeef.bugbrother.debug.validation;

import com.razeef.bugbrother.debug.config.FixValidationProperties;
import com.razeef.bugbrother.debug.exception.ModelResponseValidationException;
import com.razeef.bugbrother.debug.model.DebugMode;
import com.razeef.bugbrother.debug.model.FileChangeOperation;
import com.razeef.bugbrother.debug.model.ProposedFileChange;
import com.razeef.bugbrother.debug.model.StructuredDebugResponse;
import com.razeef.bugbrother.debug.model.ValidatedDebugResult;
import com.razeef.bugbrother.retrieval.model.ContextBundle;
import com.razeef.bugbrother.retrieval.model.ContextFile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DebugResponseValidator {

    private final RepositoryPathPolicy pathPolicy;
    private final FixValidationProperties limits;

    public DebugResponseValidator(
            RepositoryPathPolicy pathPolicy,
            FixValidationProperties limits
    ) {
        this.pathPolicy = pathPolicy;
        this.limits = limits;
    }

    public ValidatedDebugResult validate(
            DebugMode mode,
            StructuredDebugResponse response,
            ContextBundle bundle
    ) {
        if (mode == null || response == null || bundle == null) {
            throw new IllegalArgumentException(
                    "Mode, model response, and context bundle are required"
            );
        }
        if (response.additionalContextRequests() == null
                || !response.additionalContextRequests().isEmpty()) {
            throw new ModelResponseValidationException(
                    "The final model response still requests additional context"
            );
        }
        if (response.explanation() == null
                || response.explanation().isBlank()) {
            throw new ModelResponseValidationException(
                    "The model response must include an explanation"
            );
        }

        List<ProposedFileChange> changes = response.changes();
        if (changes == null) {
            throw new ModelResponseValidationException(
                    "The model response must include a changes array"
            );
        }

        if (mode == DebugMode.GUIDE_ONLY) {
            if (!changes.isEmpty()) {
                throw new ModelResponseValidationException(
                        "Guide mode cannot return repository changes"
                );
            }
            return new ValidatedDebugResult(
                    List.of(),
                    response.explanation().trim()
            );
        }

        if (changes.isEmpty()
                || changes.size() > limits.maxChangedFiles()) {
            throw new ModelResponseValidationException(
                    "Fix mode must return between 1 and "
                            + limits.maxChangedFiles()
                            + " changed files"
            );
        }

        Map<String, ContextFile> primaryByPath = new HashMap<>();
        bundle.primaryFiles().forEach(file ->
                primaryByPath.put(file.path(), file)
        );

        Set<String> seenPaths = new HashSet<>();
        List<ProposedFileChange> validated = new ArrayList<>();

        for (ProposedFileChange change : changes) {
            validated.add(validateChange(
                    change,
                    primaryByPath,
                    seenPaths
            ));
        }

        return new ValidatedDebugResult(
                List.copyOf(validated),
                response.explanation().trim()
        );
    }

    private ProposedFileChange validateChange(
            ProposedFileChange change,
            Map<String, ContextFile> primaryByPath,
            Set<String> seenPaths
    ) {
        if (change == null) {
            throw new ModelResponseValidationException(
                    "The changes array cannot contain null"
            );
        }

        String path = pathPolicy.normalize(change.path());
        if (!seenPaths.add(path)) {
            throw new ModelResponseValidationException(
                    "The model returned a duplicate changed path: " + path
            );
        }
        if (change.operation() != FileChangeOperation.UPDATE) {
            throw new ModelResponseValidationException(
                    "Only UPDATE operations are currently supported"
            );
        }

        ContextFile baseFile = primaryByPath.get(path);
        if (baseFile == null) {
            throw new ModelResponseValidationException(
                    "The model attempted to change a file outside primary context: "
                            + path
            );
        }
        if (change.baseContentSha256() == null
                || !change.baseContentSha256().equalsIgnoreCase(
                        baseFile.contentSha256()
                )) {
            throw new ModelResponseValidationException(
                    "The base content hash does not match for " + path
            );
        }
        if (change.content() == null || change.content().isBlank()) {
            throw new ModelResponseValidationException(
                    "Corrected content is required for " + path
            );
        }
        if (change.content().length() > limits.maxFileCharacters()) {
            throw new ModelResponseValidationException(
                    "Corrected content exceeds the file limit for " + path
            );
        }
        if (change.content().equals(baseFile.content())) {
            throw new ModelResponseValidationException(
                    "The model returned unchanged content for " + path
            );
        }
        if (change.reason() == null || change.reason().isBlank()) {
            throw new ModelResponseValidationException(
                    "A change reason is required for " + path
            );
        }

        return new ProposedFileChange(
                path,
                FileChangeOperation.UPDATE,
                baseFile.contentSha256(),
                change.content(),
                change.reason().trim()
        );
    }
}
