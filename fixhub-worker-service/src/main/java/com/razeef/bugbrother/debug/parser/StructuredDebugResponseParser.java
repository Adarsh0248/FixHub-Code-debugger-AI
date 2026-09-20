package com.razeef.bugbrother.debug.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razeef.bugbrother.debug.config.FixValidationProperties;
import com.razeef.bugbrother.debug.exception.ModelResponseValidationException;
import com.razeef.bugbrother.debug.model.StructuredDebugResponse;
import org.springframework.stereotype.Component;

@Component
public class StructuredDebugResponseParser {

    private final ObjectMapper objectMapper;
    private final FixValidationProperties limits;

    public StructuredDebugResponseParser(
            ObjectMapper objectMapper,
            FixValidationProperties limits
    ) {
        this.objectMapper = objectMapper.copy().enable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
        );
        this.limits = limits;
    }

    public StructuredDebugResponse parse(String response) {
        if (response == null || response.isBlank()) {
            throw new ModelResponseValidationException(
                    "The model returned an empty response"
            );
        }
        if (response.length() > limits.maxResponseCharacters()) {
            throw new ModelResponseValidationException(
                    "The model response exceeded the configured size limit"
            );
        }

        String json = removeOptionalJsonFence(response.trim());

        try {
            StructuredDebugResponse parsed = objectMapper.readValue(
                    json,
                    StructuredDebugResponse.class
            );

            if (parsed == null) {
                throw new ModelResponseValidationException(
                        "The model returned an empty JSON document"
                );
            }

            if (parsed.changes() == null
                    || parsed.additionalContextRequests() == null
                    || parsed.explanation() == null) {
                throw new ModelResponseValidationException(
                        "The model response is missing required JSON fields"
                );
            }

            return parsed;
        } catch (JsonProcessingException exception) {
            throw new ModelResponseValidationException(
                    "The model did not return valid structured JSON",
                    exception
            );
        }
    }

    private String removeOptionalJsonFence(String response) {
        if (!response.startsWith("```")) {
            return response;
        }

        int firstLineEnd = response.indexOf('\n');
        if (firstLineEnd < 0 || !response.endsWith("```")) {
            throw new ModelResponseValidationException(
                    "The model returned an incomplete JSON code fence"
            );
        }

        String fence = response.substring(0, firstLineEnd).trim();
        if (!fence.equals("```")
                && !fence.equalsIgnoreCase("```json")) {
            throw new ModelResponseValidationException(
                    "The model used an unsupported response code fence"
            );
        }

        return response.substring(
                firstLineEnd + 1,
                response.length() - 3
        ).trim();
    }
}
