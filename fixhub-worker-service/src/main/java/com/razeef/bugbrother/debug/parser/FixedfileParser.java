package com.razeef.bugbrother.debug.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razeef.bugbrother.github.service.CommitService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.*;
@Component
@Slf4j
public class FixedfileParser {

    private static final Logger logger = LoggerFactory.getLogger(FixedfileParser.class);

    public List<CommitService.FixedFile> parseFixedFiles(String response) {
        List<CommitService.FixedFile> fixedFiles = new ArrayList<>();

        // Try multiple parsing strategies

        // Strategy 1: Original format with ==== File ====
        fixedFiles = parseWithOriginalFormat(response);
        if (!fixedFiles.isEmpty()) {
            logger.info("Successfully parsed {} files using original format", fixedFiles.size());
            return fixedFiles;
        }

        // Strategy 2: JSON format
        fixedFiles = parseJsonFormat(response);
        if (!fixedFiles.isEmpty()) {
            logger.info("Successfully parsed {} files using JSON format", fixedFiles.size());
            return fixedFiles;
        }

        // Strategy 3: Markdown-style headers
        fixedFiles = parseMarkdownFormat(response);
        if (!fixedFiles.isEmpty()) {
            logger.info("Successfully parsed {} files using Markdown format", fixedFiles.size());
            return fixedFiles;
        }

        // Strategy 4: Simple file/content blocks
        fixedFiles = parseSimpleFormat(response);
        if (!fixedFiles.isEmpty()) {
            logger.info("Successfully parsed {} files using simple format", fixedFiles.size());
            return fixedFiles;
        }

        logger.warn("No corrected files could be parsed from the model response");

        return fixedFiles;
    }

    // Original parsing logic
    private List<CommitService.FixedFile> parseWithOriginalFormat(String response) {
        List<CommitService.FixedFile> fixedFiles = new ArrayList<>();

        Pattern fileHeaderPattern = Pattern.compile(
                "====\\s*File:\\s*([^=\\r\\n]+?)\\s*===="
        );
        Pattern codeBlockPattern = Pattern.compile(
                "```[\\w.+-]*\\s*\\R(.*?)\\R```",
                Pattern.DOTALL
        );

        Matcher fileHeaderMatcher = fileHeaderPattern.matcher(response);

        while (fileHeaderMatcher.find()) {
            String fileName = fileHeaderMatcher.group(1).trim();
            int startPos = fileHeaderMatcher.end();

            int endPos = response.length();
            int currentStart = fileHeaderMatcher.start();

            // Find next file header
            Matcher nextHeaderMatcher = fileHeaderPattern.matcher(response);
            nextHeaderMatcher.region(fileHeaderMatcher.end(), response.length());
            if (nextHeaderMatcher.find()) {
                endPos = nextHeaderMatcher.start();
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

    // Parse JSON format like [{"path": "...", "content": "..."}, ...]
    private List<CommitService.FixedFile> parseJsonFormat(String response) {
        List<CommitService.FixedFile> fixedFiles = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response);

            if (rootNode.isArray()) {
                for (JsonNode fileNode : rootNode) {
                    String path = getJsonString(fileNode, "path", "fileName", "file");
                    String content = getJsonString(fileNode, "content", "fixedContent", "code");

                    if (path != null && content != null) {
                        fixedFiles.add(new CommitService.FixedFile(path, content));
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("JSON parsing failed: {}", e.getMessage());
        }

        return fixedFiles;
    }

    // Parse markdown-style headers like ## filename.java
    private List<CommitService.FixedFile> parseMarkdownFormat(String response) {
        List<CommitService.FixedFile> fixedFiles = new ArrayList<>();

        Pattern fileHeaderPattern = Pattern.compile("#+\\s*(.+?\\.java)\\s*\\n");
        Pattern codeBlockPattern = Pattern.compile("```(?:java)?\\s*\\n(.*?)\\n```", Pattern.DOTALL);

        Matcher fileHeaderMatcher = fileHeaderPattern.matcher(response);

        while (fileHeaderMatcher.find()) {
            String fileName = fileHeaderMatcher.group(1).trim();
            int startPos = fileHeaderMatcher.end();

            int endPos = response.length();

            // Find next header
            Matcher nextHeaderMatcher = fileHeaderPattern.matcher(response);
            nextHeaderMatcher.region(startPos, response.length());
            if (nextHeaderMatcher.find()) {
                endPos = nextHeaderMatcher.start();
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

    // Parse simple format with file paths followed by code blocks
    private List<CommitService.FixedFile> parseSimpleFormat(String response) {
        List<CommitService.FixedFile> fixedFiles = new ArrayList<>();

        // Look for patterns like "File: path/to/file.java" followed by code
        Pattern filePathPattern = Pattern.compile("(?:File:|Path:|Filename:)\\s*([^\\n]+\\.java)", Pattern.CASE_INSENSITIVE);
        Pattern codeBlockPattern = Pattern.compile("```(?:java)?\\s*\\n(.*?)\\n```", Pattern.DOTALL);

        Matcher filePathMatcher = filePathPattern.matcher(response);

        while (filePathMatcher.find()) {
            String fileName = filePathMatcher.group(1).trim();
            int startPos = filePathMatcher.end();

            int endPos = response.length();

            // Find next file path
            Matcher nextPathMatcher = filePathPattern.matcher(response);
            nextPathMatcher.region(startPos, response.length());
            if (nextPathMatcher.find()) {
                endPos = nextPathMatcher.start();
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

    // Helper method to get string value from JSON node with multiple possible keys
    private String getJsonString(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode valueNode = node.get(key);
            if (valueNode != null && !valueNode.isNull()) {
                return valueNode.asText();
            }
        }
        return null;
    }
}
