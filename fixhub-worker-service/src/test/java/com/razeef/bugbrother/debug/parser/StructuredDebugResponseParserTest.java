package com.razeef.bugbrother.debug.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razeef.bugbrother.debug.config.FixValidationProperties;
import com.razeef.bugbrother.debug.exception.ModelResponseValidationException;
import com.razeef.bugbrother.debug.model.StructuredDebugResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructuredDebugResponseParserTest {

    private StructuredDebugResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new StructuredDebugResponseParser(
                new ObjectMapper(),
                new FixValidationProperties(8, 50_000, 1_000_000)
        );
    }

    @Test
    void parsesStrictJsonResponse() {
        StructuredDebugResponse response = parser.parse("""
                {
                  "changes": [],
                  "additionalContextRequests": [],
                  "explanation": "Check the null result"
                }
                """);

        assertEquals("Check the null result", response.explanation());
    }

    @Test
    void rejectsUnknownFieldsAndNonJsonText() {
        assertThrows(
                ModelResponseValidationException.class,
                () -> parser.parse("""
                        {
                          "changes": [],
                          "additionalContextRequests": [],
                          "explanation": "Guide",
                          "unexpected": true
                        }
                        """)
        );

        assertThrows(
                ModelResponseValidationException.class,
                () -> parser.parse("Here is the answer")
        );
    }
}
