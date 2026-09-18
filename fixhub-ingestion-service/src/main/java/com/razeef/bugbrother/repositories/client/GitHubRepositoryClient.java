package com.razeef.bugbrother.repositories.client;

import com.razeef.bugbrother.repositories.exception.GitHubApiException;
import com.razeef.bugbrother.repositories.model.GitHubApiError;
import com.razeef.bugbrother.repositories.model.GitHubBranchData;
import com.razeef.bugbrother.repositories.model.GitHubBranchPage;
import com.razeef.bugbrother.repositories.model.GitHubRepositoryData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Arrays;
import java.util.List;

@Component
public class GitHubRepositoryClient {

    private static final int MAX_PAGE_SIZE = 100;

    private final RestClient restClient;

    public GitHubRepositoryClient(
            RestClient.Builder restClientBuilder
    ) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.github.com")
                .defaultHeader(
                        "Accept",
                        "application/vnd.github+json"
                )
                .defaultHeader(
                        "X-GitHub-Api-Version",
                        "2022-11-28"
                )
                .defaultHeader(
                        "User-Agent",
                        "BugBrother"
                )
                .build();
    }

    public GitHubRepositoryData getRepository(
            String owner,
            String repo,
            String accessToken
    ) {
        validateRepositoryInput(owner, repo, accessToken);

        try {
            RepositoryPayload response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment(
                                    "repos",
                                    owner.trim(),
                                    repo.trim()
                            )
                            .build()
                    )
                    .header(
                            "Authorization",
                            "Bearer " + accessToken
                    )
                    .retrieve()
                    .body(RepositoryPayload.class);

            return toRepositoryData(response);
        } catch (RestClientResponseException exception) {
            throw mapGitHubError(
                    exception,
                    "Repository was not found or the authenticated user cannot access it"
            );
        } catch (RestClientException exception) {
            throw upstreamUnavailable(exception);
        }
    }

    public GitHubBranchPage listBranches(
            String owner,
            String repo,
            String accessToken,
            int page,
            int pageSize
    ) {
        validateRepositoryInput(owner, repo, accessToken);
        validatePage(page, pageSize);

        try {
            ResponseEntity<BranchPayload[]> response =
                    restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .pathSegment(
                                            "repos",
                                            owner.trim(),
                                            repo.trim(),
                                            "branches"
                                    )
                                    .queryParam(
                                            "page",
                                            page
                                    )
                                    .queryParam(
                                            "per_page",
                                            pageSize
                                    )
                                    .build()
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + accessToken
                            )
                            .retrieve()
                            .toEntity(BranchPayload[].class);

            BranchPayload[] payloads =
                    response.getBody();

            List<GitHubBranchData> branches =
                    payloads == null
                            ? List.of()
                            : Arrays.stream(payloads)
                                    .map(branch -> toBranchData(branch))
                                    .toList();

            String linkHeader = response
                    .getHeaders()
                    .getFirst(HttpHeaders.LINK);

            boolean hasNext = linkHeader != null
                    && linkHeader.contains("rel=\"next\"");

            return new GitHubBranchPage(
                    branches,
                    page,
                    pageSize,
                    hasNext
            );
        } catch (RestClientResponseException exception) {
            throw mapGitHubError(
                    exception,
                    "Repository was not found or its branches are inaccessible"
            );
        } catch (RestClientException exception) {
            throw upstreamUnavailable(exception);
        }
    }

    public GitHubBranchData resolveBranch(
            String owner,
            String repo,
            String branch,
            String accessToken
    ) {
        validateRepositoryInput(owner, repo, accessToken);

        if (branch == null || branch.isBlank()) {
            throw new IllegalArgumentException(
                    "Branch name is required"
            );
        }

        try {
            BranchPayload response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment(
                                    "repos",
                                    owner.trim(),
                                    repo.trim(),
                                    "branches",
                                    branch.trim()
                            )
                            .build()
                    )
                    .header(
                            "Authorization",
                            "Bearer " + accessToken
                    )
                    .retrieve()
                    .body(BranchPayload.class);

            return toBranchData(response);
        } catch (RestClientResponseException exception) {
            throw mapGitHubError(
                    exception,
                    "Branch was not found or is inaccessible"
            );
        } catch (RestClientException exception) {
            throw upstreamUnavailable(exception);
        }
    }

    private void validateRepositoryInput(
            String owner,
            String repo,
            String accessToken
    ) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException(
                    "Repository owner is required"
            );
        }

        if (repo == null || repo.isBlank()) {
            throw new IllegalArgumentException(
                    "Repository name is required"
            );
        }

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException(
                    "GitHub access token is required"
            );
        }
    }

    private void validatePage(
            int page,
            int pageSize
    ) {
        if (page < 1) {
            throw new IllegalArgumentException(
                    "Page must be at least 1"
            );
        }

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and "
                            + MAX_PAGE_SIZE
            );
        }
    }

    private GitHubRepositoryData toRepositoryData(
            RepositoryPayload repository
    ) {
        if (repository == null
                || repository.id() == null
                || repository.owner() == null
                || repository.owner().login() == null
                || repository.name() == null
                || repository.fullName() == null
                || repository.defaultBranch() == null) {
            throw new GitHubApiException(
                    GitHubApiError.INVALID_RESPONSE,
                    "GitHub returned incomplete repository metadata"
            );
        }

        PermissionsPayload permissions =
                repository.permissions();

        boolean canPull = permissions == null
                || permissions.pull();

        boolean canPush = permissions != null
                && permissions.push();

        boolean admin = permissions != null
                && permissions.admin();

        return new GitHubRepositoryData(
                repository.id(),
                repository.owner().login(),
                repository.name(),
                repository.fullName(),
                repository.privateRepository(),
                repository.defaultBranch(),
                canPull,
                canPush,
                admin
        );
    }

    private GitHubBranchData toBranchData(
            BranchPayload branch
    ) {
        if (branch == null
                || branch.name() == null
                || branch.commit() == null
                || branch.commit().sha() == null) {
            throw new GitHubApiException(
                    GitHubApiError.INVALID_RESPONSE,
                    "GitHub returned incomplete branch metadata"
            );
        }

        return new GitHubBranchData(
                branch.name(),
                branch.commit().sha(),
                branch.protectedBranch()
        );
    }

    private GitHubApiException mapGitHubError(
            RestClientResponseException exception,
            String notFoundMessage
    ) {
        int status = exception.getStatusCode().value();

        GitHubApiError error;
        String message;

        switch (status) {
            case 401 -> {
                error = GitHubApiError.AUTHENTICATION_FAILED;
                message = "GitHub authentication failed";
            }

            case 403 -> {
                String remaining = exception
                        .getResponseHeaders() == null
                        ? null
                        : exception
                                .getResponseHeaders()
                                .getFirst("X-RateLimit-Remaining");

                if ("0".equals(remaining)) {
                    error = GitHubApiError.RATE_LIMITED;
                    message =
                            "GitHub API rate limit was exceeded";
                } else {
                    error = GitHubApiError.ACCESS_DENIED;
                    message =
                            "GitHub denied access to this resource";
                }
            }

            case 404 -> {
                error =
                        GitHubApiError.NOT_FOUND_OR_INACCESSIBLE;
                message = notFoundMessage;
            }

            case 429 -> {
                error = GitHubApiError.RATE_LIMITED;
                message =
                        "GitHub API rate limit was exceeded";
            }

            default -> {
                error = GitHubApiError.UPSTREAM_FAILURE;
                message =
                        "GitHub request failed with HTTP "
                                + status;
            }
        }

        return new GitHubApiException(
                error,
                message,
                exception
        );
    }

    private GitHubApiException upstreamUnavailable(
            RestClientException exception
    ) {
        return new GitHubApiException(
                GitHubApiError.UPSTREAM_UNAVAILABLE,
                "Could not connect to the GitHub API",
                exception
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RepositoryPayload(
            Long id,
            String name,

            @JsonProperty("full_name")
            String fullName,

            @JsonProperty("private")
            boolean privateRepository,

            @JsonProperty("default_branch")
            String defaultBranch,

            OwnerPayload owner,
            PermissionsPayload permissions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OwnerPayload(
            String login
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PermissionsPayload(
            boolean pull,
            boolean push,
            boolean admin
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BranchPayload(
            String name,
            CommitPayload commit,

            @JsonProperty("protected")
            boolean protectedBranch
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CommitPayload(
            String sha
    ) {
    }
}