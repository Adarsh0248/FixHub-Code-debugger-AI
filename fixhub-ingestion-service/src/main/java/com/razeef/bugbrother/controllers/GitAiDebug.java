package com.razeef.bugbrother.controllers;

import com.razeef.bugbrother.models.CodeGuardianTask;
import com.razeef.bugbrother.models.ResponsePayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
public class GitAiDebug {

    @Autowired
    private KafkaTemplate<String, CodeGuardianTask> kafkaTemplate;

    @Autowired
    private com.razeef.bugbrother.services.GitAuthService gitAuthService;

    private static final String TOPIC = "code-guardian-tasks";

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

            String token = gitAuthService.getGitHubAccessToken();
            if (token == null || token.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("GitHub access token not available");
            }

            CodeGuardianTask task = new CodeGuardianTask(owner, repo, payload, token);
            kafkaTemplate.send(TOPIC, task);

            return ResponseEntity.accepted().body("Task accepted and sent to worker queue");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error during debugging ingestion: " + e.getMessage());
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
        return ResponseEntity.ok("Git AI Debug Ingestion Service is running");
    }
}
