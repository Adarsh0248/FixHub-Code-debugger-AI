package com.razeef.bugbrother.github.service;

import com.razeef.bugbrother.github.client.GitHubGitDataClient;
import com.razeef.bugbrother.github.exception.GitHubCommitException;
import com.razeef.bugbrother.github.exception.StaleBaseCommitException;
import com.razeef.bugbrother.github.model.AtomicCommitResult;
import com.razeef.bugbrother.github.model.GitTreeEntry;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CommitService {

    private static final Set<String> SUPPORTED_FILE_MODES = Set.of(
            "100644",
            "100755"
    );

    private final GitHubGitDataClient gitHubClient;

    public CommitService(GitHubGitDataClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }

    public AtomicCommitResult createAtomicFixCommit(
            UUID taskId,
            String owner,
            String repo,
            String selectedBranch,
            String baseCommitSha,
            List<FixedFile> fixedFiles,
            String githubToken
    ) {
        if (taskId == null) {
            throw new GitHubCommitException("Task ID is required");
        }
        requireText(owner, "owner");
        requireText(repo, "repo");
        requireText(selectedBranch, "selectedBranch");
        requireText(baseCommitSha, "baseCommitSha");
        requireText(githubToken, "githubToken");

        if (fixedFiles == null || fixedFiles.isEmpty()) {
            throw new GitHubCommitException(
                    "At least one validated file is required"
            );
        }

        String fixBranch = "ai-fix/task-" + taskId;
        String commitMessage = "BugBrother: apply validated fix for task "
                + taskId;
        String existingHead = gitHubClient.findBranchHead(
                owner, repo, fixBranch, githubToken);
        if (existingHead != null) {
            return verifyExistingFixBranch(owner, repo, fixBranch,
                    existingHead, baseCommitSha, commitMessage, githubToken);
        }

        ensureBranchStillAtBase(
                owner,
                repo,
                selectedBranch,
                baseCommitSha,
                githubToken
        );

        String baseTreeSha = gitHubClient.getCommitTreeSha(
                owner,
                repo,
                baseCommitSha,
                githubToken
        );

        Map<String, GitTreeEntry> baseEntries = new HashMap<>();
        for (GitTreeEntry entry : gitHubClient.getRecursiveTree(
                owner,
                repo,
                baseTreeSha,
                githubToken
        )) {
            baseEntries.put(entry.path(), entry);
        }

        List<ValidatedFile> validatedFiles = validateFiles(
                fixedFiles,
                baseEntries
        );

        List<GitTreeEntry> changedEntries = validatedFiles
                .stream()
                .map(file -> {
                    String blobSha = gitHubClient.createBlob(
                            owner,
                            repo,
                            file.content(),
                            githubToken
                    );

                    return new GitTreeEntry(
                            file.path(),
                            file.mode(),
                            "blob",
                            blobSha
                    );
                })
                .toList();

        String newTreeSha = gitHubClient.createTree(
                owner,
                repo,
                baseTreeSha,
                changedEntries,
                githubToken
        );

        String newCommitSha = gitHubClient.createCommit(
                owner,
                repo,
                newTreeSha,
                baseCommitSha,
                commitMessage,
                githubToken
        );

        ensureBranchStillAtBase(
                owner,
                repo,
                selectedBranch,
                baseCommitSha,
                githubToken
        );

        try {
            gitHubClient.createBranch(owner, repo, fixBranch,
                    newCommitSha, githubToken);
        } catch (GitHubCommitException exception) {
            String publishedHead = gitHubClient.findBranchHead(
                    owner, repo, fixBranch, githubToken);
            if (publishedHead == null) {
                throw exception;
            }
            return verifyExistingFixBranch(owner, repo, fixBranch,
                    publishedHead, baseCommitSha, commitMessage,
                    githubToken);
        }

        return result(owner, repo, fixBranch, newCommitSha);
    }

    private AtomicCommitResult verifyExistingFixBranch(
            String owner, String repo, String branch, String head,
            String baseCommitSha, String expectedMessage, String token) {
        GitHubGitDataClient.GitCommitIdentity identity =
                gitHubClient.getCommitIdentity(owner, repo, head, token);
        if (!baseCommitSha.equals(identity.parentSha())
                || !expectedMessage.equals(identity.message())) {
            throw new GitHubCommitException(
                    "Existing fix branch has changed; refusing to overwrite it");
        }
        return result(owner, repo, branch, head);
    }

    private AtomicCommitResult result(String owner, String repo,
            String branch, String sha) {
        return new AtomicCommitResult(branch, sha,
                "https://github.com/" + owner + "/" + repo
                        + "/tree/" + branch);
    }

    private void ensureBranchStillAtBase(
            String owner,
            String repo,
            String selectedBranch,
            String baseCommitSha,
            String githubToken
    ) {
        String currentHead = gitHubClient.getBranchHead(
                owner,
                repo,
                selectedBranch,
                githubToken
        );

        if (!baseCommitSha.equals(currentHead)) {
            throw new StaleBaseCommitException(
                    "The selected branch changed after debugging started. "
                            + "Reindex the latest commit and retry."
            );
        }
    }

    private List<ValidatedFile> validateFiles(
            List<FixedFile> fixedFiles,
            Map<String, GitTreeEntry> baseEntries
    ) {
        Set<String> seenPaths = new HashSet<>();

        return fixedFiles.stream()
                .map(file -> validateFile(
                        file,
                        baseEntries,
                        seenPaths
                ))
                .sorted(Comparator.comparing(ValidatedFile::path))
                .toList();
    }

    private ValidatedFile validateFile(
            FixedFile file,
            Map<String, GitTreeEntry> baseEntries,
            Set<String> seenPaths
    ) {
        if (file == null
                || file.path() == null
                || file.path().isBlank()
                || file.fixedContent() == null) {
            throw new GitHubCommitException(
                    "Corrected file path and content are required"
            );
        }

        if (!seenPaths.add(file.path())) {
            throw new GitHubCommitException(
                    "Duplicate corrected path: " + file.path()
            );
        }

        GitTreeEntry baseEntry = baseEntries.get(file.path());
        if (baseEntry == null) {
            throw new GitHubCommitException(
                    "Corrected file is missing from the base tree: "
                            + file.path()
            );
        }
        if (!"blob".equals(baseEntry.type())) {
            throw new GitHubCommitException(
                    "Corrected path is not a Git blob: " + file.path()
            );
        }
        if (!SUPPORTED_FILE_MODES.contains(baseEntry.mode())) {
            throw new GitHubCommitException(
                    "Unsupported Git file mode for " + file.path()
            );
        }

        return new ValidatedFile(
                file.path(),
                file.fixedContent(),
                baseEntry.mode()
        );
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new GitHubCommitException(field + " is required");
        }
    }

    private record ValidatedFile(
            String path,
            String content,
            String mode
    ) {
    }

    public record FixedFile(String path, String fixedContent) {
    }
}
