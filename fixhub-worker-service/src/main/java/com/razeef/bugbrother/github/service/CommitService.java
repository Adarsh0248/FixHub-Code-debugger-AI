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
            String owner,
            String repo,
            String selectedBranch,
            String baseCommitSha,
            List<FixedFile> fixedFiles,
            String githubToken
    ) {
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
                "BugBrother: apply validated fix",
                githubToken
        );

        ensureBranchStillAtBase(
                owner,
                repo,
                selectedBranch,
                baseCommitSha,
                githubToken
        );

        String fixBranch = "ai-fix/" + UUID.randomUUID();
        gitHubClient.createBranch(
                owner,
                repo,
                fixBranch,
                newCommitSha,
                githubToken
        );

        return new AtomicCommitResult(
                fixBranch,
                newCommitSha,
                "https://github.com/"
                        + owner
                        + "/"
                        + repo
                        + "/tree/"
                        + fixBranch
        );
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
