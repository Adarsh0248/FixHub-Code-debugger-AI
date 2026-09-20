package com.razeef.bugbrother.dependencies.service;

import com.razeef.bugbrother.dependencies.model.DependencyGraphBuildResult;
import com.razeef.bugbrother.indexes.model.IndexedSourceFileEntity;
import com.razeef.bugbrother.indexes.model.IndexedSymbolEntity;
import com.razeef.bugbrother.indexes.model.IndexedSymbolKind;
import com.razeef.bugbrother.indexes.model.SourceDependencyType;
import com.razeef.bugbrother.indexes.model.SourceFileDependencyEntity;
import com.razeef.bugbrother.indexes.repository.IndexedSourceFileRepository;
import com.razeef.bugbrother.indexes.repository.IndexedSymbolRepository;
import com.razeef.bugbrother.indexes.repository.SourceFileDependencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DependencyGraphService {

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile(
                    "^\\s*package\\s+"
                            + "([A-Za-z_$][\\w$]*"
                            + "(?:\\.[A-Za-z_$][\\w$]*)*)"
                            + "\\s*;",
                    Pattern.MULTILINE
            );

    private static final Pattern TYPE_PATTERN =
            Pattern.compile(
                    "\\b(class|interface|enum|record)"
                            + "\\s+([A-Za-z_$][\\w$]*)\\b"
            );

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile(
                    "^\\s*import\\s+"
                            + "(static\\s+)?"
                            + "([A-Za-z_$][\\w$]*"
                            + "(?:\\.[A-Za-z_$*][\\w$*]*)+)"
                            + "\\s*;",
                    Pattern.MULTILINE
            );

    private static final Pattern TYPE_TOKEN_PATTERN =
            Pattern.compile(
                    "\\b[A-Z][A-Za-z0-9_$]*\\b"
            );

    private final IndexedSourceFileRepository fileRepository;
    private final IndexedSymbolRepository symbolRepository;
    private final SourceFileDependencyRepository
            dependencyRepository;

    public DependencyGraphService(
            IndexedSourceFileRepository fileRepository,
            IndexedSymbolRepository symbolRepository,
            SourceFileDependencyRepository
                    dependencyRepository
    ) {
        this.fileRepository = fileRepository;
        this.symbolRepository = symbolRepository;
        this.dependencyRepository = dependencyRepository;
    }

    @Transactional
    public DependencyGraphBuildResult rebuild(
            UUID generationId
    ) {
        if (generationId == null) {
            throw new IllegalArgumentException(
                    "generationId is required"
            );
        }

        List<IndexedSourceFileEntity> allFiles =
                fileRepository
                        .findByGenerationIdOrderByPathAsc(
                                generationId
                        );

        List<IndexedSourceFileEntity> javaFiles =
                allFiles.stream()
                        .filter(this::isJava)
                        .toList();

        dependencyRepository.deleteByGenerationId(
                generationId
        );
        symbolRepository.deleteByGenerationId(
                generationId
        );

        List<DeclaredSymbol> declarations =
                new ArrayList<>();

        for (IndexedSourceFileEntity file : javaFiles) {
            parsePrimaryDeclaration(file)
                    .ifPresent(declarations::add);
        }

        symbolRepository.saveAll(
                declarations.stream()
                        .map(declaration ->
                                IndexedSymbolEntity.create(
                                        generationId,
                                        declaration.file()
                                                .getFileId(),
                                        declaration.symbolName(),
                                        declaration.qualifiedName(),
                                        declaration.symbolKind()
                                )
                        )
                        .toList()
        );

        Map<String, DeclaredSymbol> byQualifiedName =
                new HashMap<>();

        Map<String, List<DeclaredSymbol>> bySimpleName =
                new HashMap<>();

        for (DeclaredSymbol declaration : declarations) {
            byQualifiedName.put(
                    declaration.qualifiedName(),
                    declaration
            );

            bySimpleName
                    .computeIfAbsent(
                            declaration.symbolName(),
                            ignored -> new ArrayList<>()
                    )
                    .add(declaration);
        }

        Map<EdgeKey, SourceFileDependencyEntity> edges =
                new LinkedHashMap<>();

        for (IndexedSourceFileEntity source : javaFiles) {
            addImportEdges(
                    generationId,
                    source,
                    byQualifiedName,
                    edges
            );

            addSymbolReferenceEdges(
                    generationId,
                    source,
                    bySimpleName,
                    edges
            );
        }

        dependencyRepository.saveAll(edges.values());

        return new DependencyGraphBuildResult(
                generationId,
                javaFiles.size(),
                declarations.size(),
                edges.size()
        );
    }

    private java.util.Optional<DeclaredSymbol>
    parsePrimaryDeclaration(
            IndexedSourceFileEntity file
    ) {
        Matcher typeMatcher =
                TYPE_PATTERN.matcher(file.getContent());

        if (!typeMatcher.find()) {
            return java.util.Optional.empty();
        }

        String keyword = typeMatcher.group(1)
                .toUpperCase(Locale.ROOT);

        String symbolName = typeMatcher.group(2);

        String packageName =
                extractPackage(file.getContent());

        String qualifiedName = packageName.isBlank()
                ? symbolName
                : packageName + "." + symbolName;

        return java.util.Optional.of(
                new DeclaredSymbol(
                        file,
                        symbolName,
                        qualifiedName,
                        IndexedSymbolKind.valueOf(keyword)
                )
        );
    }

    private void addImportEdges(
            UUID generationId,
            IndexedSourceFileEntity source,
            Map<String, DeclaredSymbol> byQualifiedName,
            Map<EdgeKey, SourceFileDependencyEntity> edges
    ) {
        Matcher matcher =
                IMPORT_PATTERN.matcher(source.getContent());

        while (matcher.find()) {
            boolean staticImport =
                    matcher.group(1) != null;

            String importedName =
                    matcher.group(2);

            DeclaredSymbol target =
                    resolveImport(
                            importedName,
                            staticImport,
                            byQualifiedName
                    );

            if (target == null
                    || target.file().getFileId()
                            .equals(source.getFileId())) {
                continue;
            }

            addEdge(
                    generationId,
                    source,
                    target.file(),
                    SourceDependencyType.IMPORT,
                    "import " + importedName,
                    edges
            );
        }
    }

    private DeclaredSymbol resolveImport(
            String importedName,
            boolean staticImport,
            Map<String, DeclaredSymbol> byQualifiedName
    ) {
        if (importedName.endsWith(".*")) {
            return null;
        }

        DeclaredSymbol direct =
                byQualifiedName.get(importedName);

        if (direct != null || !staticImport) {
            return direct;
        }

        String candidate = importedName;

        while (candidate.contains(".")) {
            candidate = candidate.substring(
                    0,
                    candidate.lastIndexOf('.')
            );

            DeclaredSymbol target =
                    byQualifiedName.get(candidate);

            if (target != null) {
                return target;
            }
        }

        return null;
    }

    private void addSymbolReferenceEdges(
            UUID generationId,
            IndexedSourceFileEntity source,
            Map<String, List<DeclaredSymbol>> bySimpleName,
            Map<EdgeKey, SourceFileDependencyEntity> edges
    ) {
        Matcher matcher =
                TYPE_TOKEN_PATTERN.matcher(
                        source.getContent()
                );

        Set<String> inspectedSymbols =
                new HashSet<>();

        while (matcher.find()) {
            String symbolName = matcher.group();

            if (!inspectedSymbols.add(symbolName)) {
                continue;
            }

            List<DeclaredSymbol> candidates =
                    bySimpleName.get(symbolName);

            if (candidates == null
                    || candidates.size() != 1) {
                continue;
            }

            DeclaredSymbol target =
                    candidates.getFirst();

            if (target.file().getFileId()
                    .equals(source.getFileId())) {
                continue;
            }

            addEdge(
                    generationId,
                    source,
                    target.file(),
                    SourceDependencyType.SYMBOL_REFERENCE,
                    "symbol " + symbolName,
                    edges
            );
        }
    }

    private void addEdge(
            UUID generationId,
            IndexedSourceFileEntity source,
            IndexedSourceFileEntity target,
            SourceDependencyType type,
            String evidence,
            Map<EdgeKey, SourceFileDependencyEntity> edges
    ) {
        EdgeKey key = new EdgeKey(
                source.getFileId(),
                target.getFileId(),
                type
        );

        edges.putIfAbsent(
                key,
                SourceFileDependencyEntity.create(
                        generationId,
                        source.getFileId(),
                        target.getFileId(),
                        type,
                        evidence
                )
        );
    }

    private String extractPackage(String content) {
        Matcher matcher =
                PACKAGE_PATTERN.matcher(content);

        return matcher.find()
                ? matcher.group(1)
                : "";
    }

    private boolean isJava(
            IndexedSourceFileEntity file
    ) {
        return "java".equalsIgnoreCase(
                file.getLanguage()
        ) || file.getPath()
                .toLowerCase(Locale.ROOT)
                .endsWith(".java");
    }

    private record DeclaredSymbol(
            IndexedSourceFileEntity file,
            String symbolName,
            String qualifiedName,
            IndexedSymbolKind symbolKind
    ) {
    }

    private record EdgeKey(
            UUID sourceFileId,
            UUID targetFileId,
            SourceDependencyType type
    ) {
    }
}