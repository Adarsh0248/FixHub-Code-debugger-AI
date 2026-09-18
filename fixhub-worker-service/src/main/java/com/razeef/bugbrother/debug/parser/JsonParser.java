package com.razeef.bugbrother.debug.parser;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razeef.bugbrother.github.service.CommitService;
import org.springframework.stereotype.Component;

import java.util.List;
  @Component
    public class JsonParser {

        private static final ObjectMapper objectMapper = new ObjectMapper();

        public  List<CommitService.FixedFile> parseJsonToFixedFiles(String jsonResponse) {
            try {
                return objectMapper.readValue(jsonResponse, new TypeReference<List<CommitService.FixedFile>>() {});
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse JSON response to FixedFile list", e);
            }
        }

        // Alternative manual parsing if Jackson is not available
        public static List<CommitService.FixedFile> parseJsonManually(String jsonResponse) {
            // This would require manual JSON parsing logic
            // For production use, Jackson or Gson is recommended
            throw new UnsupportedOperationException("Use Jackson parsing method instead");
        }
    }

