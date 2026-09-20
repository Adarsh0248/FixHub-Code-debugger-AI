package com.razeef.bugbrother.github.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.razeef.bugbrother.github.exception.GitHubCommitException;
import com.razeef.bugbrother.github.model.GitTreeEntry;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Component
public class GitHubGitDataClient {

    private final WebClient webClient;

    public GitHubGitDataClient(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "BugBrother")
                .build();
    }

    public String getBranchHead(
            String owner,
            String repo,
            String branch,
            String token
    ) {
        try {
            GitRefResponse response = webClient.get()
                    .uri(
                            "/repos/{owner}/{repo}/git/ref/heads/{branch}",
                            owner,
                            repo,
                            branch
                    )
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .retrieve()
                    .bodyToMono(GitRefResponse.class)
                    .block();

            if (response == null
                    || response.object() == null
                    || response.object().sha() == null) {
                throw invalidResponse("branch reference");
            }
            return response.object().sha();
        } catch (WebClientResponseException exception) {
            throw requestFailed("read the selected branch", exception);
        }
    }

    public String getCommitTreeSha(
            String owner,
            String repo,
            String commitSha,
            String token
    ) {
        try {
            GitCommitResponse response = webClient.get()
                    .uri(
                            "/repos/{owner}/{repo}/git/commits/{sha}",
                            owner,
                            repo,
                            commitSha
                    )
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .retrieve()
                    .bodyToMono(GitCommitResponse.class)
                    .block();

            if (response == null
                    || response.tree() == null
                    || response.tree().sha() == null) {
                throw invalidResponse("base commit");
            }
            return response.tree().sha();
        } catch (WebClientResponseException exception) {
            throw requestFailed("read the base commit", exception);
        }
    }

    public List<GitTreeEntry> getRecursiveTree(
            String owner,
            String repo,
            String treeSha,
            String token
    ) {
        try {
            GitTreeResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/git/trees/{sha}")
                            .queryParam("recursive", "1")
                            .build(owner, repo, treeSha))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .retrieve()
                    .bodyToMono(GitTreeResponse.class)
                    .block();

            if (response == null || response.tree() == null) {
                throw invalidResponse("base tree");
            }
            if (response.truncated()) {
                throw new GitHubCommitException(
                        "GitHub truncated the base tree; atomic commit is unsafe"
                );
            }

            return response.tree().stream()
                    .map(entry -> new GitTreeEntry(
                            entry.path(),
                            entry.mode(),
                            entry.type(),
                            entry.sha()
                    ))
                    .toList();
        } catch (WebClientResponseException exception) {
            throw requestFailed("read the base tree", exception);
        }
    }

    public String createBlob(
            String owner,
            String repo,
            String content,
            String token
    ) {
        String encoded = Base64.getEncoder().encodeToString(
                content.getBytes(StandardCharsets.UTF_8)
        );

        try {
            ShaResponse response = webClient.post()
                    .uri("/repos/{owner}/{repo}/git/blobs", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .bodyValue(new CreateBlobRequest(encoded, "base64"))
                    .retrieve()
                    .bodyToMono(ShaResponse.class)
                    .block();

            return requireSha(response, "created blob");
        } catch (WebClientResponseException exception) {
            throw requestFailed("create a corrected-file blob", exception);
        }
    }

    public String createTree(
            String owner,
            String repo,
            String baseTreeSha,
            List<GitTreeEntry> changes,
            String token
    ) {
        List<CreateTreeEntryRequest> entries = changes.stream()
                .map(entry -> new CreateTreeEntryRequest(
                        entry.path(),
                        entry.mode(),
                        entry.type(),
                        entry.sha()
                ))
                .toList();

        try {
            ShaResponse response = webClient.post()
                    .uri("/repos/{owner}/{repo}/git/trees", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .bodyValue(new CreateTreeRequest(baseTreeSha, entries))
                    .retrieve()
                    .bodyToMono(ShaResponse.class)
                    .block();

            return requireSha(response, "created tree");
        } catch (WebClientResponseException exception) {
            throw requestFailed("create the corrected Git tree", exception);
        }
    }

    public String createCommit(
            String owner,
            String repo,
            String treeSha,
            String parentCommitSha,
            String message,
            String token
    ) {
        try {
            ShaResponse response = webClient.post()
                    .uri("/repos/{owner}/{repo}/git/commits", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .bodyValue(new CreateCommitRequest(
                            message,
                            treeSha,
                            List.of(parentCommitSha)
                    ))
                    .retrieve()
                    .bodyToMono(ShaResponse.class)
                    .block();

            return requireSha(response, "created commit");
        } catch (WebClientResponseException exception) {
            throw requestFailed("create the atomic fix commit", exception);
        }
    }

    public void createBranch(
            String owner,
            String repo,
            String branch,
            String commitSha,
            String token
    ) {
        try {
            webClient.post()
                    .uri("/repos/{owner}/{repo}/git/refs", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .bodyValue(new CreateRefRequest(
                            "refs/heads/" + branch,
                            commitSha
                    ))
                    .retrieve()
                    .bodyToMono(GitRefResponse.class)
                    .block();
        } catch (WebClientResponseException exception) {
            throw requestFailed("create the fix branch", exception);
        }
    }

    private String bearer(String token) {
        if (token == null || token.isBlank()) {
            throw new GitHubCommitException(
                    "GitHub token is required for fix commits"
            );
        }
        return "Bearer " + token;
    }

    private String requireSha(ShaResponse response, String resource) {
        if (response == null || response.sha() == null) {
            throw invalidResponse(resource);
        }
        return response.sha();
    }

    private GitHubCommitException invalidResponse(String resource) {
        return new GitHubCommitException(
                "GitHub returned an invalid " + resource + " response"
        );
    }

    private GitHubCommitException requestFailed(
            String action,
            WebClientResponseException exception
    ) {
        return new GitHubCommitException(
                "GitHub could not "
                        + action
                        + " (HTTP "
                        + exception.getStatusCode().value()
                        + ")",
                exception
        );
    }

    private record GitObjectResponse(String sha) {
    }

    private record GitRefResponse(
            @JsonProperty("object") GitObjectResponse object
    ) {
    }

    private record GitTreePointer(String sha) {
    }

    private record GitCommitResponse(
            String sha,
            GitTreePointer tree
    ) {
    }

    private record GitTreeEntryResponse(
            String path,
            String mode,
            String type,
            String sha
    ) {
    }

    private record GitTreeResponse(
            String sha,
            List<GitTreeEntryResponse> tree,
            boolean truncated
    ) {
    }

    private record ShaResponse(String sha) {
    }

    private record CreateBlobRequest(
            String content,
            String encoding
    ) {
    }

    private record CreateTreeEntryRequest(
            String path,
            String mode,
            String type,
            String sha
    ) {
    }

    private record CreateTreeRequest(
            @JsonProperty("base_tree") String baseTree,
            List<CreateTreeEntryRequest> tree
    ) {
    }

    private record CreateCommitRequest(
            String message,
            String tree,
            List<String> parents
    ) {
    }

    private record CreateRefRequest(
            String ref,
            String sha
    ) {
    }
}
