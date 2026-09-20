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
import com.razeef.bugbrother.retrieval.model.ContextFileRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DebugResponseValidatorTest {

    private DebugResponseValidator validator;
    private ContextBundle bundle;

    @BeforeEach
    void setUp() {
        validator = new DebugResponseValidator(
                new RepositoryPathPolicy(),
                new FixValidationProperties(8, 50_000, 1_000_000)
        );

        ContextFile primary = file(
                "src/UserService.java",
                "primary-hash",
                "class UserService {}",
                ContextFileRole.PRIMARY
        );
        ContextFile supporting = file(
                "src/UserRepository.java",
                "support-hash",
                "interface UserRepository {}",
                ContextFileRole.SUPPORTING
        );

        bundle = new ContextBundle(
                UUID.randomUUID(),
                "commit-sha",
                "error",
                List.of(primary),
                List.of(supporting),
                List.of(),
                100,
                25
        );
    }

    @Test
    void acceptsUpdateForPrimaryFileWithMatchingHash() {
        StructuredDebugResponse response = response(change(
                "src/UserService.java",
                "primary-hash",
                "class UserService { void fix() {} }"
        ));

        ValidatedDebugResult result = validator.validate(
                DebugMode.FIX_AND_COMMIT,
                response,
                bundle
        );

        assertEquals(1, result.changes().size());
        assertEquals("src/UserService.java", result.changes().getFirst().path());
    }

    @Test
    void rejectsSupportingFileAndWrongBaseHash() {
        StructuredDebugResponse supportingChange = response(change(
                "src/UserRepository.java",
                "support-hash",
                "interface UserRepository { void fix(); }"
        ));
        StructuredDebugResponse wrongHash = response(change(
                "src/UserService.java",
                "wrong-hash",
                "class UserService { void fix() {} }"
        ));

        assertThrows(
                ModelResponseValidationException.class,
                () -> validator.validate(
                        DebugMode.FIX_AND_COMMIT,
                        supportingChange,
                        bundle
                )
        );
        assertThrows(
                ModelResponseValidationException.class,
                () -> validator.validate(
                        DebugMode.FIX_AND_COMMIT,
                        wrongHash,
                        bundle
                )
        );
    }

    @Test
    void rejectsPathTraversal() {
        StructuredDebugResponse response = response(change(
                "../outside.txt",
                "primary-hash",
                "unsafe"
        ));

        assertThrows(
                ModelResponseValidationException.class,
                () -> validator.validate(
                        DebugMode.FIX_AND_COMMIT,
                        response,
                        bundle
                )
        );
    }

    private StructuredDebugResponse response(ProposedFileChange change) {
        return new StructuredDebugResponse(
                List.of(change),
                List.of(),
                "Explanation"
        );
    }

    private ProposedFileChange change(
            String path,
            String hash,
            String content
    ) {
        return new ProposedFileChange(
                path,
                FileChangeOperation.UPDATE,
                hash,
                content,
                "Required fix"
        );
    }

    private ContextFile file(
            String path,
            String hash,
            String content,
            ContextFileRole role
    ) {
        return new ContextFile(
                UUID.randomUUID(),
                path,
                "java",
                hash,
                content,
                role,
                "test",
                1,
                content.length()
        );
    }
}
