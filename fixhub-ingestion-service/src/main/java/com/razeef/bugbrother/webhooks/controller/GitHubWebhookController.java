package com.razeef.bugbrother.webhooks.controller;

import com.razeef.bugbrother.webhooks.service.GitHubWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
public class GitHubWebhookController {

    private final GitHubWebhookService webhookService;

    public GitHubWebhookController(
            GitHubWebhookService webhookService
    ) {
        this.webhookService = webhookService;
    }

    @PostMapping("/github")
    public ResponseEntity<Void> receive(
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestBody byte[] body
    ) {
        webhookService.handle(
                signature,
                eventType,
                deliveryId,
                body
        );
        return ResponseEntity.accepted().build();
    }
}
