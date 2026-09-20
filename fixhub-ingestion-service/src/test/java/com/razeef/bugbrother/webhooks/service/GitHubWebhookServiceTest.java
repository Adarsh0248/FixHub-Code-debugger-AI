package com.razeef.bugbrother.webhooks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razeef.bugbrother.auth.service.GitAuthService;
import com.razeef.bugbrother.indexes.dto.response.IndexTaskSubmissionResult;
import com.razeef.bugbrother.indexes.model.ActiveIndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.repository.ActiveIndexGenerationRepository;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;
import com.razeef.bugbrother.indexes.service.IndexTaskSubmissionService;
import com.razeef.bugbrother.repositories.repository.RepositoryRepository;
import com.razeef.bugbrother.repositories.model.RepositoryEntity;
import com.razeef.bugbrother.tasks.model.TaskEntity;
import com.razeef.bugbrother.webhooks.model.GitHubWebhookDeliveryEntity;
import com.razeef.bugbrother.webhooks.repository.GitHubWebhookDeliveryRepository;
import com.razeef.bugbrother.webhooks.security.GitHubWebhookSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubWebhookServiceTest {

    private GitHubWebhookSignatureVerifier signatureVerifier;
    private GitHubWebhookDeliveryRepository deliveryRepository;
    private RepositoryRepository repositoryRepository;
    private ActiveIndexGenerationRepository activeRepository;
    private IndexGenerationRepository generationRepository;
    private GitAuthService gitAuthService;
    private IndexTaskSubmissionService submissionService;
    private GitHubWebhookService service;

    @BeforeEach
    void setUp() {
        signatureVerifier = mock(GitHubWebhookSignatureVerifier.class);
        deliveryRepository = mock(GitHubWebhookDeliveryRepository.class);
        repositoryRepository = mock(RepositoryRepository.class);
        activeRepository = mock(ActiveIndexGenerationRepository.class);
        generationRepository = mock(IndexGenerationRepository.class);
        gitAuthService = mock(GitAuthService.class);
        submissionService = mock(IndexTaskSubmissionService.class);

        service = new GitHubWebhookService(
                signatureVerifier,
                new ObjectMapper(),
                deliveryRepository,
                repositoryRepository,
                activeRepository,
                generationRepository,
                gitAuthService,
                submissionService
        );
    }

    @Test
    void ignoresDuplicateDeliveryBeforeParsingOrQueueing() {
        when(deliveryRepository.existsById("delivery-1"))
                .thenReturn(true);

        service.handle(
                "sha256=ignored-by-mock",
                "push",
                "delivery-1",
                "not-json".getBytes(StandardCharsets.UTF_8)
        );

        verify(repositoryRepository, never())
                .findByGithubRepositoryId(any());
        verify(submissionService, never())
                .submitForUser(any(), any(), any());
    }

    @Test
    void recordsValidPushWhenRepositoryIsNotTracked() {
        when(deliveryRepository.existsById("delivery-2"))
                .thenReturn(false);
        when(repositoryRepository.findByGithubRepositoryId(99L))
                .thenReturn(List.of());

        String payload = """
                {
                  "ref": "refs/heads/main",
                  "after": "1111111111111111111111111111111111111111",
                  "deleted": false,
                  "repository": {"id": 99}
                }
                """;

        service.handle(
                "sha256=ignored-by-mock",
                "push",
                "delivery-2",
                payload.getBytes(StandardCharsets.UTF_8)
        );

        verify(deliveryRepository).saveAndFlush(
                any(GitHubWebhookDeliveryEntity.class)
        );
        verify(submissionService, never())
                .submitForUser(any(), any(), any());
    }

    @Test
    void queuesNewGenerationForTrackedBranchAtNewCommit() {
        String userId = "github:123";
        UUID generationId = UUID.randomUUID();
        RepositoryEntity repository = RepositoryEntity.discovered(
                userId,
                99L,
                "owner",
                "repo",
                "owner/repo",
                true,
                "main",
                true,
                true,
                true
        );
        ActiveIndexGenerationEntity active =
                ActiveIndexGenerationEntity.create(
                        userId,
                        99L,
                        "main",
                        generationId
                );
        IndexGenerationEntity generation =
                mock(IndexGenerationEntity.class);

        when(deliveryRepository.existsById("delivery-3"))
                .thenReturn(false);
        when(repositoryRepository.findByGithubRepositoryId(99L))
                .thenReturn(List.of(repository));
        when(activeRepository.findByUserIdAndRepositoryIdAndBranch(
                userId,
                99L,
                "main"
        )).thenReturn(Optional.of(active));
        when(generationRepository.findById(generationId))
                .thenReturn(Optional.of(generation));
        when(generation.getCommitSha()).thenReturn(
                "2222222222222222222222222222222222222222"
        );
        when(gitAuthService.getGitHubAccessTokenForUser(userId))
                .thenReturn("token");
        when(submissionService.submitForUser(
                org.mockito.ArgumentMatchers.eq(userId),
                any(),
                org.mockito.ArgumentMatchers.eq("token")
        )).thenReturn(new IndexTaskSubmissionResult(
                mock(TaskEntity.class),
                true
        ));

        String payload = """
                {
                  "ref": "refs/heads/main",
                  "after": "1111111111111111111111111111111111111111",
                  "deleted": false,
                  "repository": {"id": 99}
                }
                """;

        service.handle(
                "sha256=ignored-by-mock",
                "push",
                "delivery-3",
                payload.getBytes(StandardCharsets.UTF_8)
        );

        verify(submissionService).submitForUser(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.argThat(revision ->
                        revision.selectedBranch().equals("main")
                                && revision.commitSha().equals(
                                "1111111111111111111111111111111111111111"
                        )
                ),
                org.mockito.ArgumentMatchers.eq("token")
        );
        verify(deliveryRepository).saveAndFlush(any());
    }
}
