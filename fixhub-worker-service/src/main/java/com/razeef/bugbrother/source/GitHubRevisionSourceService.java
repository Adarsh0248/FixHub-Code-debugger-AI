package com.razeef.bugbrother.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Service
public class GitHubRevisionSourceService {

    private final WebClient webClient;
    private final RepositoryFilePolicy filePolicy;

    public GitHubRevisionSourceService(
            WebClient.Builder webClientBuilder,
            RepositoryFilePolicy filePolicy
    ) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.github.com")
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        "application/vnd.github+json"
                )
                .defaultHeader(
                        "X-GitHub-Api-Version",
                        "2022-11-28"
                )
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "BugBrother"
                )
                .build();

        this.filePolicy = filePolicy;
    }

    public List<RepositorySourceFile> fetchRevision(
            String owner,
            String repo,
            String commitSha,
            String accessToken
    ) {
        validateInput(
                owner,
                repo,
                commitSha,
                accessToken
        );

        try {
            String treeSha = resolveTreeSha(
                    owner.trim(),
                    repo.trim(),
                    commitSha.trim(),
                    accessToken
            );

            GitTreePayload tree = fetchTree(
                    owner.trim(),
                    repo.trim(),
                    treeSha,
                    accessToken
            );

            if (tree == null || tree.tree() == null) {
                throw new RepositorySourceException(
                        "GitHub returned an invalid repository tree"
                );
            }

            if (tree.truncated()) {
                throw new RepositorySourceException(
                        "GitHub truncated the repository tree; "
                                + "partial indexing is not allowed"
                );
            }

            List<RepositorySourceFile> files =
                    new ArrayList<>();

            for (GitTreeEntryPayload entry : tree.tree()) {
                if (!filePolicy.shouldInclude(
                        entry.path(),
                        entry.type(),
                        entry.size()
                )) {
                    continue;
                }

                RepositorySourceFile file = fetchBlob(
                        owner.trim(),
                        repo.trim(),
                        entry,
                        accessToken
                );

                files.add(file);
            }

            files.sort(
                    Comparator.comparing(
                            RepositorySourceFile::path
                    )
            );

            return List.copyOf(files);
        } catch (RepositorySourceException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            throw mapGitHubFailure(exception);
        } catch (RuntimeException exception) {
            throw new RepositorySourceException(
                    "Could not retrieve repository revision",
                    exception
            );
        }
    }

    private String resolveTreeSha(
            String owner,
            String repo,
            String commitSha,
            String accessToken
    ) {
        GitCommitPayload commit = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(
                                "repos",
                                owner,
                                repo,
                                "git",
                                "commits",
                                commitSha
                        )
                        .build()
                )
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .retrieve()
                .bodyToMono(GitCommitPayload.class)
                .block();

        if (commit == null
                || commit.tree() == null
                || commit.tree().sha() == null
                || commit.tree().sha().isBlank()) {
            throw new RepositorySourceException(
                    "GitHub returned an invalid commit object"
            );
        }

        return commit.tree().sha();
    }

    private GitTreePayload fetchTree(
            String owner,
            String repo,
            String treeSha,
            String accessToken
    ) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(
                                "repos",
                                owner,
                                repo,
                                "git",
                                "trees",
                                treeSha
                        )
                        .queryParam("recursive", "1")
                        .build()
                )
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .retrieve()
                .bodyToMono(GitTreePayload.class)
                .block();
    }

    private RepositorySourceFile fetchBlob(
            String owner,
            String repo,
            GitTreeEntryPayload entry,
            String accessToken
    ) {
        if (entry.sha() == null || entry.sha().isBlank()) {
            throw new RepositorySourceException(
                    "Tree entry has no blob SHA: "
                            + entry.path()
            );
        }

        GitBlobPayload blob = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(
                                "repos",
                                owner,
                                repo,
                                "git",
                                "blobs",
                                entry.sha()
                        )
                        .build()
                )
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .retrieve()
                .bodyToMono(GitBlobPayload.class)
                .block();

        if (blob == null
                || blob.content() == null
                || !"base64".equalsIgnoreCase(blob.encoding())) {
            throw new RepositorySourceException(
                    "GitHub returned an unsupported blob for "
                            + entry.path()
            );
        }

        byte[] decoded;

        try {
            decoded = Base64
                    .getMimeDecoder()
                    .decode(blob.content());
        } catch (IllegalArgumentException exception) {
            throw new RepositorySourceException(
                    "GitHub returned invalid Base64 for "
                            + entry.path(),
                    exception
            );
        }

        String content =
                new String(decoded, StandardCharsets.UTF_8);

        return new RepositorySourceFile(
                entry.path(),
                content,
                entry.sha(),
                sha256(decoded),
                decoded.length
        );
    }

    private String sha256(
            byte[] content
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private RepositorySourceException mapGitHubFailure(
            WebClientResponseException exception
    ) {
        int status = exception
                .getStatusCode()
                .value();

        String message = switch (status) {
            case 401 ->
                    "GitHub authentication failed while reading the revision";

            case 403 ->
                    "GitHub denied access while reading the revision";

            case 404 ->
                    "Repository, commit, tree, or blob was not found";

            case 409 ->
                    "GitHub could not build a tree for this repository";

            case 429 ->
                    "GitHub rate limit was exceeded";

            default ->
                    "GitHub revision request failed with HTTP "
                            + status;
        };

        return new RepositorySourceException(
                message,
                exception
        );
    }

    private void validateInput(
            String owner,
            String repo,
            String commitSha,
            String accessToken
    ) {
        requireText(owner, "Repository owner");
        requireText(repo, "Repository name");
        requireText(commitSha, "Commit SHA");
        requireText(accessToken, "GitHub access token");
    }

    private void requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitCommitPayload(
            GitObjectReference tree
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitObjectReference(
            String sha
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitTreePayload(
            boolean truncated,
            List<GitTreeEntryPayload> tree
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitTreeEntryPayload(
            String path,
            String type,
            String sha,
            Long size
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitBlobPayload(
            String content,
            String encoding,
            Long size
    ) {
    }
}