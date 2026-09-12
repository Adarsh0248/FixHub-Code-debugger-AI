package com.razeef.bugbrother.controllers;

import com.razeef.bugbrother.models.IndexRepoTask;
import com.razeef.bugbrother.services.GitAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bulk-ingests a repo's files into the vector search index up front, so RAG
 * has something to retrieve on a repo's very first debug request instead of
 * only picking up content as a side effect of a prior fix already having run.
 */
@RestController
@RequestMapping("/api/repos")
public class IndexController {

    private static final String TOPIC = "code-guardian-index-tasks";

    @Autowired
    private KafkaTemplate<String, IndexRepoTask> kafkaTemplate;

    @Autowired
    private GitAuthService gitAuthService;

    @PostMapping("/{owner}/{repo}/index")
    public ResponseEntity<?> index(@PathVariable String owner, @PathVariable String repo) {
        if (owner == null || owner.trim().isEmpty() || repo == null || repo.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Owner and repository name cannot be empty");
        }

        String token;
        try {
            token = gitAuthService.getGitHubAccessToken();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }

        kafkaTemplate.send(TOPIC, new IndexRepoTask(owner, repo, token));
        return ResponseEntity.accepted().body("Indexing started for " + owner + "/" + repo);
    }
}
