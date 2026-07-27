package com.razeef.bugbrother.parsers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razeef.bugbrother.services.CommitService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RobustParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<CommitService.FixedFile> parseJsonToFixedFiles(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("Response is null or empty");
        }

        // Debug: Print first 100 characters of response
        System.out.println("Response preview: " + response.substring(0, Math.min(100, response.length())));

        // Clean and validate the response
        String cleanedResponse = cleanJsonResponse(response);

        try {
            // Validate JSON format
            if (!isValidJson(cleanedResponse)) {
                System.err.println("Invalid JSON detected, attempting fallback parsing...");
                return parseWithFallback(response);
            }

            return objectMapper.readValue(cleanedResponse, new TypeReference<List<CommitService.FixedFile>>() {});

        } catch (Exception e) {
            System.err.println("JSON parsing failed: " + e.getMessage());
            System.err.println("Attempting fallback parsing...");
            return parseWithFallback(response);
        }
    }

    private static String cleanJsonResponse(String response) {
        // Remove any leading/trailing whitespace
        response = response.trim();

        // Remove any non-JSON prefixes (like "data=" or similar)
        if (response.startsWith("=")) {
            response = response.substring(1);
        }

        // Remove any URL-encoded or other prefixes
        int jsonStart = findJsonStart(response);
        if (jsonStart > 0) {
            response = response.substring(jsonStart);
        }

        return response.trim();
    }

    private static int findJsonStart(String response) {
        // Look for the first occurrence of '[' or '{'
        int bracketIndex = response.indexOf('[');
        int braceIndex = response.indexOf('{');

        if (bracketIndex == -1 && braceIndex == -1) {
            return 0; // No JSON found
        }

        if (bracketIndex == -1) return braceIndex;
        if (braceIndex == -1) return bracketIndex;

        return Math.min(bracketIndex, braceIndex);
    }

    private static boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Fallback parser for non-JSON responses (like your original markdown format)
    private static List<CommitService.FixedFile> parseWithFallback(String response) {
        List<CommitService.FixedFile> fixedFiles = new ArrayList<>();

        try {
            // Try to parse as the original markdown format
            if (response.contains("==== File ") && response.contains("```java")) {
                return parseMarkdownFormat(response);
            }

            // Try to parse as malformed JSON
            if (response.contains("\"path\"") && response.contains("\"fixedContent\"")) {
                return parseQuasiJson(response);
            }

            System.err.println("Unable to parse response with any known format");
            return fixedFiles;

        } catch (Exception e) {
            System.err.println("Fallback parsing also failed: " + e.getMessage());
            return fixedFiles;
        }
    }

    private static List<CommitService.FixedFile> parseMarkdownFormat(String response) {
        List<CommitService.FixedFile> fixedFiles = new ArrayList<>();

        Pattern fileHeaderPattern = Pattern.compile("==== File ([^=]+) ====");
        Pattern codeBlockPattern = Pattern.compile("```java\\s*\\n(.*?)\\n```", Pattern.DOTALL);

        Matcher fileHeaderMatcher = fileHeaderPattern.matcher(response);

        while (fileHeaderMatcher.find()) {
            String fileName = fileHeaderMatcher.group(1).trim();
            int startPos = fileHeaderMatcher.end();

            int endPos = response.length();
            if (fileHeaderMatcher.find()) {
                endPos = fileHeaderMatcher.start();
                fileHeaderMatcher.region(fileHeaderMatcher.start(), response.length());
            }

            String sectionContent = response.substring(startPos, endPos);
            Matcher codeBlockMatcher = codeBlockPattern.matcher(sectionContent);

            if (codeBlockMatcher.find()) {
                String codeContent = codeBlockMatcher.group(1);
                fixedFiles.add(new CommitService.FixedFile(fileName, codeContent));
            }
        }

        return fixedFiles;
    }

    private static List<CommitService.FixedFile> parseQuasiJson(String response) {
        List<CommitService.FixedFile> fixedFiles = new ArrayList<>();

        // Extract individual file entries using regex
        Pattern filePattern = Pattern.compile("\\{\\s*\"path\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"fixedContent\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\}", Pattern.DOTALL);
        Matcher matcher = filePattern.matcher(response);

        while (matcher.find()) {
            String path = matcher.group(1);
            String content = matcher.group(2);

            // Unescape the content
            content = unescapeJsonString(content);

            fixedFiles.add(new CommitService.FixedFile(path, content));
        }

        return fixedFiles;
    }

    private static String unescapeJsonString(String escaped) {
        return escaped
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    // Debug method to help identify the response format
    public static void debugResponse(String response) {
        System.out.println("=== Response Debug Info ===");
        System.out.println("Response length: " + (response != null ? response.length() : "null"));

        if (response != null) {
            System.out.println("First 200 characters:");
            System.out.println(response.substring(0, Math.min(200, response.length())));

            System.out.println("\nFirst character code: " + (int) response.charAt(0));
            System.out.println("Starts with '[': " + response.startsWith("["));
            System.out.println("Starts with '{': " + response.startsWith("{"));
            System.out.println("Contains '====': " + response.contains("===="));
            System.out.println("Contains '```java': " + response.contains("```java"));
            System.out.println("Contains '\"path\"': " + response.contains("\"path\""));
        }
        System.out.println("=========================");
    }
}
