package com.razeef.bugbrother.github.service;

import com.razeef.bugbrother.github.client.GitHubGitDataClient;
import com.razeef.bugbrother.github.exception.GitHubCommitException;
import com.razeef.bugbrother.github.exception.StaleBaseCommitException;
import com.razeef.bugbrother.github.model.AtomicCommitResult;
import com.razeef.bugbrother.github.model.GitTreeEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommitServiceTest {

    private static final UUID TASK_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final String COMMIT_MESSAGE =
            "BugBrother: apply validated fix for task " + TASK_ID;

    private GitHubGitDataClient gitHubClient;
    private CommitService commitService;

    @BeforeEach
    void setUp() {
        gitHubClient = mock(GitHubGitDataClient.class);
        commitService = new CommitService(gitHubClient);
    }

    @Test
    void createsBranchOnlyAfterAllGitObjectsAreReady() {
        when(gitHubClient.getBranchHead(
                "owner", "repo", "feature/login", "token"
        )).thenReturn("base-commit");
        when(gitHubClient.getCommitTreeSha(
                "owner", "repo", "base-commit", "token"
        )).thenReturn("base-tree");
        when(gitHubClient.getRecursiveTree(
                "owner", "repo", "base-tree", "token"
        )).thenReturn(List.of(
                new GitTreeEntry("src/A.java", "100644", "blob", "old-a"),
                new GitTreeEntry("script.sh", "100755", "blob", "old-script")
        ));
        when(gitHubClient.createBlob(
                "owner", "repo", "new-a", "token"
        )).thenReturn("blob-a");
        when(gitHubClient.createBlob(
                "owner", "repo", "new-script", "token"
        )).thenReturn("blob-script");
        when(gitHubClient.createTree(
                org.mockito.ArgumentMatchers.eq("owner"),
                org.mockito.ArgumentMatchers.eq("repo"),
                org.mockito.ArgumentMatchers.eq("base-tree"),
                anyList(),
                org.mockito.ArgumentMatchers.eq("token")
        )).thenReturn("new-tree");
        when(gitHubClient.createCommit(
                "owner",
                "repo",
                "new-tree",
                "base-commit",
                COMMIT_MESSAGE,
                "token"
        )).thenReturn("new-commit");

        AtomicCommitResult result = commitService.createAtomicFixCommit(
                TASK_ID,
                "owner",
                "repo",
                "feature/login",
                "base-commit",
                List.of(
                        new CommitService.FixedFile("script.sh", "new-script"),
                        new CommitService.FixedFile("src/A.java", "new-a")
                ),
                "token"
        );

        InOrder order = inOrder(gitHubClient);
        order.verify(gitHubClient).findBranchHead(
                "owner", "repo", "ai-fix/task-" + TASK_ID, "token"
        );
        order.verify(gitHubClient).getBranchHead(
                "owner", "repo", "feature/login", "token"
        );
        order.verify(gitHubClient).getCommitTreeSha(
                "owner", "repo", "base-commit", "token"
        );
        order.verify(gitHubClient).getRecursiveTree(
                "owner", "repo", "base-tree", "token"
        );
        order.verify(gitHubClient).createBlob(
                "owner", "repo", "new-script", "token"
        );
        order.verify(gitHubClient).createBlob(
                "owner", "repo", "new-a", "token"
        );
        order.verify(gitHubClient).createTree(
                org.mockito.ArgumentMatchers.eq("owner"),
                org.mockito.ArgumentMatchers.eq("repo"),
                org.mockito.ArgumentMatchers.eq("base-tree"),
                anyList(),
                org.mockito.ArgumentMatchers.eq("token")
        );
        order.verify(gitHubClient).createCommit(
                "owner",
                "repo",
                "new-tree",
                "base-commit",
                COMMIT_MESSAGE,
                "token"
        );
        order.verify(gitHubClient).getBranchHead(
                "owner", "repo", "feature/login", "token"
        );
        order.verify(gitHubClient).createBranch(
                org.mockito.ArgumentMatchers.eq("owner"),
                org.mockito.ArgumentMatchers.eq("repo"),
                anyString(),
                org.mockito.ArgumentMatchers.eq("new-commit"),
                org.mockito.ArgumentMatchers.eq("token")
        );

        assertEquals("new-commit", result.commitSha());
        assertEquals("ai-fix/task-" + TASK_ID, result.branchName());
    }

    @Test
    void reusesExistingTaskBranchAfterWorkerReplay() {
        when(gitHubClient.findBranchHead(
                "owner", "repo", "ai-fix/task-" + TASK_ID, "token"
        )).thenReturn("fix-commit");
        when(gitHubClient.getCommitIdentity(
                "owner", "repo", "fix-commit", "token"
        )).thenReturn(new GitHubGitDataClient.GitCommitIdentity(
                COMMIT_MESSAGE, "base-commit"));

        AtomicCommitResult result = commitService.createAtomicFixCommit(
                TASK_ID, "owner", "repo", "main", "base-commit",
                List.of(new CommitService.FixedFile("src/A.java", "fixed")),
                "token");

        assertEquals("fix-commit", result.commitSha());
        verify(gitHubClient, never()).createBlob(
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectsExistingTaskBranchWithUnexpectedParent() {
        when(gitHubClient.findBranchHead(
                "owner", "repo", "ai-fix/task-" + TASK_ID, "token"
        )).thenReturn("other-commit");
        when(gitHubClient.getCommitIdentity(
                "owner", "repo", "other-commit", "token"
        )).thenReturn(new GitHubGitDataClient.GitCommitIdentity(
                COMMIT_MESSAGE, "different-base"));

        assertThrows(GitHubCommitException.class,
                () -> commitService.createAtomicFixCommit(
                        TASK_ID, "owner", "repo", "main", "base-commit",
                        List.of(new CommitService.FixedFile(
                                "src/A.java", "fixed")), "token"));
    }

    @Test
    void stopsBeforeCreatingObjectsWhenSelectedBranchMoved() {
        when(gitHubClient.getBranchHead(
                "owner", "repo", "main", "token"
        )).thenReturn("newer-commit");

        assertThrows(
                StaleBaseCommitException.class,
                () -> commitService.createAtomicFixCommit(
                        TASK_ID,
                        "owner",
                        "repo",
                        "main",
                        "indexed-commit",
                        List.of(new CommitService.FixedFile(
                                "src/A.java",
                                "new-a"
                        )),
                        "token"
                )
        );

        verify(gitHubClient, never()).createBlob(
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
        verify(gitHubClient, never()).createBranch(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }
}
