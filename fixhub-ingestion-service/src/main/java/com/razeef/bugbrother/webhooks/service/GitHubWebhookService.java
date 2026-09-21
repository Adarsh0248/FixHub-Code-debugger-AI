package com.razeef.bugbrother.webhooks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razeef.bugbrother.auth.service.GitAuthService;
import com.razeef.bugbrother.indexes.dto.response.IndexTaskSubmissionResult;
import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.repository.ActiveIndexGenerationRepository;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;
import com.razeef.bugbrother.indexes.service.IndexTaskSubmissionService;
import com.razeef.bugbrother.repositories.dto.response.RepositoryResponse;
import com.razeef.bugbrother.repositories.model.RepositoryEntity;
import com.razeef.bugbrother.repositories.repository.RepositoryRepository;
import com.razeef.bugbrother.webhooks.model.GitHubPushPayload;
import com.razeef.bugbrother.webhooks.model.GitHubWebhookDeliveryEntity;
import com.razeef.bugbrother.webhooks.repository.GitHubWebhookDeliveryRepository;
import com.razeef.bugbrother.webhooks.security.GitHubWebhookSignatureVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.io.IOException;

@Service
public class GitHubWebhookService {

    private static final String PUSH_EVENT = "push";
    private static final String BRANCH_REF_PREFIX = "refs/heads/";
    private static final String DELETED_SHA = "0".repeat(40);

    private final GitHubWebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final GitHubWebhookDeliveryRepository deliveryRepository;
    private final RepositoryRepository repositoryRepository;
    private final ActiveIndexGenerationRepository activeRepository;
    private final IndexGenerationRepository generationRepository;
    private final GitAuthService gitAuthService;
    private final IndexTaskSubmissionService submissionService;

    public GitHubWebhookService(
            GitHubWebhookSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper,
            GitHubWebhookDeliveryRepository deliveryRepository,
            RepositoryRepository repositoryRepository,
            ActiveIndexGenerationRepository activeRepository,
            IndexGenerationRepository generationRepository,
            GitAuthService gitAuthService,
            IndexTaskSubmissionService submissionService
    ) {
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
        this.deliveryRepository = deliveryRepository;
        this.repositoryRepository = repositoryRepository;
        this.activeRepository = activeRepository;
        this.generationRepository = generationRepository;
        this.gitAuthService = gitAuthService;
        this.submissionService = submissionService;
    }

    @Transactional
    public void handle(
            String signature,
            String eventType,
            String deliveryId,
            byte[] body
    ) {
        signatureVerifier.verify(body, signature);
        validateHeader(eventType, "GitHub event type", 64);
        validateHeader(deliveryId, "GitHub delivery ID", 128);

        if (deliveryRepository.existsById(deliveryId)) {
            return;
        }

        if (!PUSH_EVENT.equals(eventType)) {
            recordDelivery(deliveryId, eventType);
            return;
        }

        GitHubPushPayload payload = parse(body);
        if (!isIndexableBranchPush(payload)) {
            recordDelivery(deliveryId, eventType);
            return;
        }

        String branch = payload.ref().substring(
                BRANCH_REF_PREFIX.length()
        );

        for (RepositoryEntity repository : repositoryRepository
                .findByGithubRepositoryId(payload.repository().id())) {
            activeRepository
                    .findByUserIdAndRepositoryIdAndBranch(
                            repository.getUserId(),
                            repository.getGithubRepositoryId(),
                            branch
                    )
                    .ifPresent(active -> queueIfRevisionChanged(
                            repository,
                            branch,
                            payload.after(),
                            active.getGenerationId()
                    ));
        }

        recordDelivery(deliveryId, eventType);
    }

    private void queueIfRevisionChanged(
            RepositoryEntity repository,
            String branch,
            String commitSha,
            java.util.UUID activeGenerationId
    ) {
        IndexGenerationEntity activeGeneration = generationRepository
                .findById(activeGenerationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Active index generation is missing"
                ));

        if (commitSha.equalsIgnoreCase(activeGeneration.getCommitSha())) {
            return;
        }

        String githubToken = gitAuthService
                .getGitHubAccessTokenForUser(repository.getUserId());

        RepositoryResponse revision = new RepositoryResponse(
                repository.getGithubRepositoryId(),
                repository.getOwner(),
                repository.getName(),
                repository.getFullName(),
                repository.isPrivateRepository(),
                repository.getDefaultBranch(),
                branch,
                commitSha,
                repository.isCanPull(),
                repository.isCanPush(),
                repository.isAdmin(),
                Instant.now()
        );

        IndexTaskSubmissionResult submission =
                submissionService.submitForUser(
                        repository.getUserId(),
                        revision,
                        githubToken
                );

        if (!submission.published()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not queue repository reindexing"
            );
        }
    }

    private GitHubPushPayload parse(byte[] body) {
        try {
            return objectMapper.readValue(body, GitHubPushPayload.class);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid GitHub push payload",
                    exception
            );
        }
    }

    private boolean isIndexableBranchPush(GitHubPushPayload payload) {
        return payload != null
                && !payload.deleted()
                && payload.ref() != null
                && payload.ref().startsWith(BRANCH_REF_PREFIX)
                && payload.ref().length() > BRANCH_REF_PREFIX.length()
                && payload.after() != null
                && payload.after().matches("[0-9a-fA-F]{40}")
                && !DELETED_SHA.equals(payload.after())
                && payload.repository() != null
                && payload.repository().id() != null;
    }

    private void recordDelivery(String deliveryId, String eventType) {
        deliveryRepository.saveAndFlush(
                new GitHubWebhookDeliveryEntity(
                        deliveryId,
                        eventType
                )
        );
    }

    private void validateHeader(
            String value,
            String field,
            int maxLength
    ) {
        if (value == null
                || value.isBlank()
                || value.length() > maxLength
                || !value.matches("[A-Za-z0-9._-]+")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    field + " is invalid"
            );
        }
    }
}
