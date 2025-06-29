package com.razeef.BugBrother.controllers;


import com.razeef.BugBrother.Wrappers.GitAiLayer;
import com.razeef.BugBrother.models.ResponsePayload;
import com.razeef.BugBrother.parsers.FixedfileParser;
import com.razeef.BugBrother.parsers.JsonParser;
import com.razeef.BugBrother.parsers.RobustParser;
import com.razeef.BugBrother.services.CommitService;
import com.razeef.BugBrother.services.GitAuthService;
import com.razeef.BugBrother.services.GitHubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
//@RequestMapping("/api/git-ai")
public class GitAiDebug {

    @Autowired
    private GitHubService gitHubService;

    @Autowired
    private FixedfileParser parser;

    @Autowired
    private GitAuthService gitAuthService;

    @Autowired
    private CommitService commitService;

    private final GitAiLayer gitAiLayer;

    public GitAiDebug(GitAiLayer gitAiLayer) {
        this.gitAiLayer = gitAiLayer;
    }

    @GetMapping("/fetch/{owner}/{repo}")
    public ResponseEntity<?> fetch(@PathVariable String owner, @PathVariable String repo) {
        try {
            // Add input validation
            if (owner == null || owner.trim().isEmpty() || repo == null || repo.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Owner and repository name cannot be empty");
            }

            List<CommitService.FixedFile> gitServiceData = gitHubService.fetchJavaFilesFromRepo(owner, repo, "");

            if (gitServiceData.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(gitServiceData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching repository data: " + e.getMessage());
        }
    }

    @PostMapping("/debug/{owner}/{repo}")
    public ResponseEntity<?> debug(@PathVariable String owner, @PathVariable String repo,
                                   @RequestBody ResponsePayload payload) {
        try {
            // Input validation
            if (payload == null || payload.getFiles() == null || payload.getUserQ() == null) {
                return ResponseEntity.badRequest().body("Invalid payload: files and userQ are required");
            }

            if (owner == null || owner.trim().isEmpty() || repo == null || repo.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Owner and repository name cannot be empty");
            }

            String response = gitAiLayer.askAiDebug(payload.getFiles(), payload.getUserQ());

            if (response == null || response.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No debug response generated");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error during debugging: " + e.getMessage());
        }
    }

    @PostMapping("/commitcode/{owner}/{repo}")
    public ResponseEntity<String> commitFixedFiles(@PathVariable String owner, @PathVariable String repo,
                                                   @RequestBody String response) {
        try {
            // Input validation
            if (owner == null || owner.trim().isEmpty() || repo == null || repo.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Owner and repository name cannot be empty");
            }

            if (response == null || response.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Response body cannot be empty");
            }

            List<CommitService.FixedFile> files = parser.parseFixedFiles(response);

            if (files.isEmpty()) {
                return ResponseEntity.badRequest().body("No valid files found in response");
            }

            String token = gitAuthService.getGitHubAccessToken();

            if (token == null || token.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("GitHub access token not available");
            }

            System.out.println("The token is: " + token.substring(0, Math.min(token.length(), 8)) + "...");

            commitService.createFixBranchAndCommitWithLogging(owner, repo, files, token);

            return ResponseEntity.ok("Fixes committed successfully to repository: " + owner + "/" + repo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error committing fixes: " + e.getMessage());
        }
    }

    @GetMapping("/home")
    public ResponseEntity<String> home() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
            }

            return ResponseEntity.ok("Welcome " + auth.getName() + " to Git AI Debug Service");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving user information");
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Git AI Debug Service is running");
    }
}