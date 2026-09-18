package com.razeef.bugbrother.source.policy;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class RepositoryFilePolicy {

    private static final long MAX_FILE_SIZE_BYTES =
            512 * 1024;

    private static final Set<String> INCLUDED_EXTENSIONS =
            Set.of(
                    "java",
                    "kt",
                    "kts",
                    "groovy",

                    "js",
                    "jsx",
                    "ts",
                    "tsx",

                    "py",
                    "go",
                    "rs",

                    "properties",
                    "yml",
                    "yaml",
                    "json",
                    "xml",
                    "toml",

                    "gradle",
                    "sql",

                    "md"
            );

    private static final Set<String> INCLUDED_FILE_NAMES =
            Set.of(
                    "dockerfile",
                    "makefile",

                    "pom.xml",
                    "build.gradle",
                    "build.gradle.kts",
                    "settings.gradle",
                    "settings.gradle.kts",
                    "gradle.properties",

                    "docker-compose.yml",
                    "docker-compose.yaml",
                    "compose.yml",
                    "compose.yaml",

                    ".gitignore",
                    ".dockerignore"
            );

    private static final Set<String> EXCLUDED_DIRECTORIES =
            Set.of(
                    ".git",
                    ".idea",
                    ".vscode",

                    "node_modules",
                    "vendor",

                    "build",
                    "target",
                    "dist",
                    "out",
                    "coverage",

                    ".gradle",
                    ".mvn",

                    "__pycache__",
                    ".pytest_cache",
                    ".next"
            );

    private static final Set<String> EXCLUDED_FILE_NAMES =
            Set.of(
                    "package-lock.json",
                    "yarn.lock",
                    "pnpm-lock.yaml",
                    "gradle.lockfile"
            );

    public boolean shouldInclude(
            String path,
            String gitObjectType,
            Long size
    ) {
        if (path == null || path.isBlank()) {
            return false;
        }

        if (!"blob".equals(gitObjectType)) {
            return false;
        }

        if (size == null
                || size <= 0
                || size > MAX_FILE_SIZE_BYTES) {
            return false;
        }

        String normalized = path
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);

        if (containsExcludedDirectory(normalized)) {
            return false;
        }

        String fileName = fileName(normalized);

        if (EXCLUDED_FILE_NAMES.contains(fileName)) {
            return false;
        }

        if (fileName.endsWith(".min.js")
                || fileName.endsWith(".min.css")
                || fileName.endsWith(".map")) {
            return false;
        }

        if (INCLUDED_FILE_NAMES.contains(fileName)) {
            return true;
        }

        String extension = extension(fileName);

        return INCLUDED_EXTENSIONS.contains(extension);
    }

    private boolean containsExcludedDirectory(
            String normalizedPath
    ) {
        String[] segments = normalizedPath.split("/");

        for (int index = 0;
             index < segments.length - 1;
             index++) {
            if (EXCLUDED_DIRECTORIES.contains(
                    segments[index]
            )) {
                return true;
            }
        }

        return false;
    }

    private String fileName(
            String normalizedPath
    ) {
        int separator =
                normalizedPath.lastIndexOf('/');

        return separator < 0
                ? normalizedPath
                : normalizedPath.substring(separator + 1);
    }

    private String extension(
            String fileName
    ) {
        int dot = fileName.lastIndexOf('.');

        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dot + 1);
    }
}