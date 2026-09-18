package com.razeef.bugbrother.chunking;

import com.razeef.bugbrother.source.RepositorySourceFile;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DeterministicRepositoryChunker {

    public static final String CHUNKER_VERSION =
            "line-window-v1";

    private static final int LINES_PER_CHUNK = 120;
    private static final int OVERLAP_LINES = 20;
    private static final int STEP =
            LINES_PER_CHUNK - OVERLAP_LINES;

    private static final int MAX_IMPORT_LINES = 40;

    private static final Pattern JAVA_PACKAGE_PATTERN =
            Pattern.compile(
                    "^\\s*package\\s+[^;]+;",
                    Pattern.MULTILINE
            );

    private static final Pattern JAVA_IMPORT_PATTERN =
            Pattern.compile(
                    "^\\s*import\\s+[^;]+;",
                    Pattern.MULTILINE
            );

    private static final Pattern JAVA_TYPE_PATTERN =
            Pattern.compile(
                    "\\b(class|interface|enum|record)\\s+"
                            + "([A-Za-z_$][A-Za-z0-9_$]*)"
            );

    private static final Pattern JAVA_METHOD_PATTERN =
            Pattern.compile(
                    "(?m)^\\s*"
                            + "(?:(?:public|protected|private|"
                            + "static|final|synchronized|abstract|"
                            + "native|default|strictfp)\\s+)*"
                            + "[A-Za-z_$][A-Za-z0-9_$"
                            + "<>,.?\\[\\] ]*\\s+"
                            + "([A-Za-z_$][A-Za-z0-9_$]*)"
                            + "\\s*\\("
            );

    public List<RepositoryChunk> chunkRevision(
            long repositoryId,
            String repositoryFullName,
            String commitSha,
            List<RepositorySourceFile> files
    ) {
        validateRevisionInput(
                repositoryId,
                repositoryFullName,
                commitSha,
                files
        );

        List<RepositorySourceFile> orderedFiles =
                files.stream()
                        .sorted(
                                Comparator.comparing(
                                        RepositorySourceFile::path
                                )
                        )
                        .toList();

        List<RepositoryChunk> chunks =
                new ArrayList<>();

        for (RepositorySourceFile file : orderedFiles) {
            chunks.addAll(
                    chunkFile(
                            repositoryId,
                            repositoryFullName.trim(),
                            commitSha.trim(),
                            file
                    )
            );
        }

        return List.copyOf(chunks);
    }

    private List<RepositoryChunk> chunkFile(
            long repositoryId,
            String repositoryFullName,
            String commitSha,
            RepositorySourceFile file
    ) {
        String normalizedContent =
                normalizeLineEndings(file.content());

        if (normalizedContent.isBlank()
                || normalizedContent.indexOf('\0') >= 0) {
            return List.of();
        }

        String[] lines =
                normalizedContent.split("\n", -1);

        String language =
                detectLanguage(file.path());

        String fileContext =
                extractFileContext(
                        language,
                        normalizedContent
                );

        List<RepositoryChunk> chunks =
                new ArrayList<>();

        for (int startIndex = 0;
             startIndex < lines.length;
             startIndex += STEP) {

            int endExclusive = Math.min(
                    startIndex + LINES_PER_CHUNK,
                    lines.length
            );

            String sourceContent = joinLines(
                    lines,
                    startIndex,
                    endExclusive
            );

            if (sourceContent.isBlank()) {
                if (endExclusive == lines.length) {
                    break;
                }

                continue;
            }

            int startLine = startIndex + 1;
            int endLine = endExclusive;

            String symbol = detectSymbol(
                    language,
                    sourceContent
            );

            String chunkContentSha256 =
                    sha256Hex(sourceContent);

            String chunkId = createChunkId(
                    repositoryId,
                    commitSha,
                    file.path(),
                    startLine,
                    endLine,
                    chunkContentSha256
            );

            long vectorLabel =
                    labelFromChunkId(chunkId);

            String embeddingText = buildEmbeddingText(
                    repositoryFullName,
                    commitSha,
                    file.path(),
                    language,
                    symbol,
                    startLine,
                    endLine,
                    fileContext,
                    sourceContent
            );

            chunks.add(new RepositoryChunk(
                    repositoryId,
                    repositoryFullName,
                    commitSha,

                    file.path(),
                    language,
                    symbol,

                    startLine,
                    endLine,

                    file.contentSha256(),
                    chunkContentSha256,

                    chunkId,
                    vectorLabel,
                    CHUNKER_VERSION,

                    sourceContent,
                    embeddingText
            ));

            if (endExclusive == lines.length) {
                break;
            }
        }

        return chunks;
    }

    private String createChunkId(
            long repositoryId,
            String commitSha,
            String path,
            int startLine,
            int endLine,
            String chunkContentSha256
    ) {
        String identity = String.join(
                "\n",
                CHUNKER_VERSION,
                Long.toUnsignedString(repositoryId),
                commitSha,
                path,
                startLine + ":" + endLine,
                chunkContentSha256
        );

        return sha256Hex(identity);
    }

    private long labelFromChunkId(
            String chunkId
    ) {
        byte[] digest =
                HexFormat.of().parseHex(chunkId);

        long label = ByteBuffer
                .wrap(digest, 0, Long.BYTES)
                .getLong();

        return label == 0 ? 1 : label;
    }

    private String buildEmbeddingText(
            String repositoryFullName,
            String commitSha,
            String path,
            String language,
            String symbol,
            int startLine,
            int endLine,
            String fileContext,
            String sourceContent
    ) {
        StringBuilder text =
                new StringBuilder();

        text.append("repository: ")
                .append(repositoryFullName)
                .append('\n');

        text.append("revision: ")
                .append(commitSha)
                .append('\n');

        text.append("path: ")
                .append(path)
                .append('\n');

        text.append("language: ")
                .append(language)
                .append('\n');

        if (!symbol.isBlank()) {
            text.append("symbol: ")
                    .append(symbol)
                    .append('\n');
        }

        text.append("lines: ")
                .append(startLine)
                .append('-')
                .append(endLine)
                .append('\n');

        if (!fileContext.isBlank()) {
            text.append("file context:\n")
                    .append(fileContext)
                    .append('\n');
        }

        text.append("source:\n")
                .append(sourceContent);

        return text.toString();
    }

    private String extractFileContext(
            String language,
            String content
    ) {
        if (!"java".equals(language)) {
            return "";
        }

        List<String> context =
                new ArrayList<>();

        Matcher packageMatcher =
                JAVA_PACKAGE_PATTERN.matcher(content);

        if (packageMatcher.find()) {
            context.add(packageMatcher.group().trim());
        }

        Matcher importMatcher =
                JAVA_IMPORT_PATTERN.matcher(content);

        int importCount = 0;

        while (importMatcher.find()
                && importCount < MAX_IMPORT_LINES) {
            context.add(importMatcher.group().trim());
            importCount++;
        }

        return String.join("\n", context);
    }

    private String detectSymbol(
            String language,
            String sourceContent
    ) {
        if (!"java".equals(language)) {
            return "";
        }

        Matcher methodMatcher =
                JAVA_METHOD_PATTERN.matcher(sourceContent);

        if (methodMatcher.find()) {
            return methodMatcher.group(1);
        }

        Matcher typeMatcher =
                JAVA_TYPE_PATTERN.matcher(sourceContent);

        if (typeMatcher.find()) {
            return typeMatcher.group(2);
        }

        return "";
    }

    private String detectLanguage(
            String path
    ) {
        String normalized =
                path.toLowerCase(Locale.ROOT);

        if (normalized.endsWith(".java")) {
            return "java";
        }

        if (normalized.endsWith(".kt")
                || normalized.endsWith(".kts")) {
            return "kotlin";
        }

        if (normalized.endsWith(".groovy")
                || normalized.endsWith(".gradle")) {
            return "groovy";
        }

        if (normalized.endsWith(".ts")
                || normalized.endsWith(".tsx")) {
            return "typescript";
        }

        if (normalized.endsWith(".js")
                || normalized.endsWith(".jsx")) {
            return "javascript";
        }

        if (normalized.endsWith(".py")) {
            return "python";
        }

        if (normalized.endsWith(".go")) {
            return "go";
        }

        if (normalized.endsWith(".rs")) {
            return "rust";
        }

        if (normalized.endsWith(".sql")) {
            return "sql";
        }

        if (normalized.endsWith(".json")) {
            return "json";
        }

        if (normalized.endsWith(".yml")
                || normalized.endsWith(".yaml")) {
            return "yaml";
        }

        if (normalized.endsWith(".xml")) {
            return "xml";
        }

        if (normalized.endsWith(".properties")) {
            return "properties";
        }

        if (normalized.endsWith(".toml")) {
            return "toml";
        }

        if (normalized.endsWith(".md")) {
            return "markdown";
        }

        return "text";
    }

    private String normalizeLineEndings(
            String content
    ) {
        return content
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private String joinLines(
            String[] lines,
            int startInclusive,
            int endExclusive
    ) {
        StringBuilder result =
                new StringBuilder();

        for (int index = startInclusive;
             index < endExclusive;
             index++) {
            if (index > startInclusive) {
                result.append('\n');
            }

            result.append(lines[index]);
        }

        return result.toString();
    }

    private String sha256Hex(
            String value
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private void validateRevisionInput(
            long repositoryId,
            String repositoryFullName,
            String commitSha,
            List<RepositorySourceFile> files
    ) {
        if (repositoryId <= 0) {
            throw new IllegalArgumentException(
                    "repositoryId must be positive"
            );
        }

        if (repositoryFullName == null
                || repositoryFullName.isBlank()) {
            throw new IllegalArgumentException(
                    "repositoryFullName is required"
            );
        }

        if (commitSha == null || commitSha.isBlank()) {
            throw new IllegalArgumentException(
                    "commitSha is required"
            );
        }

        if (files == null) {
            throw new IllegalArgumentException(
                    "files are required"
            );
        }
    }
}