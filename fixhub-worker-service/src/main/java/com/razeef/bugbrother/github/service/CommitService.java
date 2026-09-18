package com.razeef.bugbrother.github.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class CommitService {

    private final WebClient webClient;


   // private String githubToken;

    public CommitService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.USER_AGENT, "ai-fix-client")
                .build();
    }

    public void createFixBranchAndCommit(String owner, String repo, List<FixedFile> fixedFiles,String githubToken) {
        try {
            String branchName = "ai-fix/" + UUID.randomUUID();

            // Get master branch SHA
            String masterSha = getMasterBranchSha(owner, repo,githubToken);

            // Create new branch
            createNewBranch(owner, repo, branchName, masterSha,githubToken);

            // Commit each fixed file
            for (FixedFile file : fixedFiles) {
                commitFile(owner, repo, branchName, file,githubToken);
            }

            System.out.println("AI fixed code committed to branch: " + branchName);

        } catch (Exception e) {
            System.err.println("Error creating branch and committing files: " + e.getMessage());
            throw new RuntimeException("Failed to commit fixed files", e);
        }
    }

    private String getMasterBranchSha(String owner, String repo,String githubToken) {
        Map<String, Object> response = webClient.get()
                .uri("/repos/{owner}/{repo}/git/ref/heads/main", owner, repo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("Failed to get master branch SHA");
        }

        Map<String, Object> objectMap = (Map<String, Object>) response.get("object");
        return (String) objectMap.get("sha");
    }

    private void createNewBranch(String owner, String repo, String branchName, String masterSha,String githubToken) {
        Map<String, String> createRefBody = Map.of(
                "ref", "refs/heads/" + branchName,
                "sha", masterSha
        );

        webClient.post()
                .uri("/repos/{owner}/{repo}/git/refs", owner, repo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                .bodyValue(createRefBody)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    private void commitFile(String owner, String repo, String branchName, FixedFile file,String githubToken) {
        try {
            // Get current file metadata
            Map<String, Object> fileMeta = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}?ref={branch}", owner, repo, file.path(), branchName)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .onErrorResume(error -> {
                        // File doesn't exist, return empty map
                        System.out.println("File " + file.path() + " doesn't exist, will create new file");
                        return Mono.just(Map.of());
                    })
                    .block();

            String fileSha = null;
            if (fileMeta != null && fileMeta.containsKey("sha")) {
                fileSha = (String) fileMeta.get("sha");
            }

            // Encode file content
            String encodedContent = Base64.getEncoder().encodeToString(
                    file.fixedContent().getBytes(StandardCharsets.UTF_8)
            );

            // Prepare commit body
            Map<String, Object> commitBody;
            if (fileSha != null) {
                // Update existing file
                commitBody = Map.of(
                        "message", "AI: Auto-fixed file: " + file.path(),
                        "content", encodedContent,
                        "sha", fileSha,
                        "branch", branchName
                );
            } else {
                // Create new file
                commitBody = Map.of(
                        "message", "AI: Auto-created file: " + file.path(),
                        "content", encodedContent,
                        "branch", branchName
                );
            }

            // Commit the file
            webClient.put()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, file.path())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                    .bodyValue(commitBody)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            System.out.println("Successfully committed: " + file.path());

        } catch (Exception e) {
            System.err.println("Failed to commit file " + file.path() + ": " + e.getMessage());
            throw new RuntimeException("Failed to commit file: " + file.path(), e);
        }
    }

    // Enhanced version with better error handling and logging
    public void createFixBranchAndCommitWithLogging(String owner, String repo, List<FixedFile> fixedFiles,String githubToken) {
        String branchName = "ai-fix/" + UUID.randomUUID();
        System.out.println("Starting to create branch: " + branchName);

        try {
            // Get master branch SHA
            System.out.println("Getting master branch SHA...");
            String masterSha = getMasterBranchSha(owner, repo,githubToken);
            System.out.println("Master SHA: " + masterSha);

            // Create new branch
            System.out.println("Creating new branch: " + branchName);
            createNewBranch(owner, repo, branchName, masterSha,githubToken);
            System.out.println("Branch created successfully");

            // Commit each fixed file
            System.out.println("Committing " + fixedFiles.size() + " files...");
            int successCount = 0;
            int failCount = 0;

            for (FixedFile file : fixedFiles) {
                try {
                    commitFile(owner, repo, branchName, file,githubToken);
                    successCount++;
                    System.out.println("✓ Committed: " + file.path());
                } catch (Exception e) {
                    failCount++;
                    System.err.println("✗ Failed to commit: " + file.path() + " - " + e.getMessage());
                }
            }

            System.out.println(
                        "Commit summary: "
                                + successCount
                                + " successful, "
                                + failCount
                                + " failed"
                );

                if (failCount > 0) {
                    throw new IllegalStateException(
                            "Failed to commit "
                                    + failCount
                                    + " of "
                                    + fixedFiles.size()
                                    + " corrected file(s)"
                    );
                }

            System.out.println("AI fixed code committed to branch: " + branchName);

        }  catch (WebClientResponseException e) {
            log.error("GitHub API Error: Status={}, Body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            // Check specific error messages
            if (e.getStatusCode().value() == 403) {
                log.error("Possible causes:");
                log.error("1. Token doesn't have 'repo' scope");
                log.error("2. User doesn't have write access to repository");
                log.error("3. Repository doesn't exist or is private");
            }

            throw new IllegalStateException(
                    "GitHub rejected the fix commit",
                    e
            );
        }
    }

    public record FixedFile(String path, String fixedContent) {}
}
