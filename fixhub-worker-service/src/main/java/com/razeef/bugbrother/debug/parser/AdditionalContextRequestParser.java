package com.razeef.bugbrother.debug.parser;

import com.razeef.bugbrother.retrieval.exception.ContextExpansionException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AdditionalContextRequestParser {

    private static final Pattern REQUEST_PATTERN = Pattern.compile(
            "(?m)^\\s*REQUIRED_ADDITIONAL_FILE:\\s*(.+?)\\s*$"
    );

    public List<String> parse(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> paths = new LinkedHashSet<>();
        Matcher matcher = REQUEST_PATTERN.matcher(response);

        while (matcher.find()) {
            paths.add(normalize(matcher.group(1)));
        }

        return List.copyOf(paths);
    }

    private String normalize(String value) {
        String path = value == null
                ? ""
                : value.trim().replace('\\', '/');

        while (path.startsWith("./")) {
            path = path.substring(2);
        }

        if (path.isBlank()
                || path.length() > 2000
                || path.startsWith("/")
                || path.matches("^[A-Za-z]:.*")
                || Arrays.asList(path.split("/", -1)).contains("..")) {
            throw new ContextExpansionException(
                    "The model requested an unsafe repository path"
            );
        }

        return path;
    }
}
