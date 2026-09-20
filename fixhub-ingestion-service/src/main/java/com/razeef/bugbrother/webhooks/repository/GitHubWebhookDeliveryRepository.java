package com.razeef.bugbrother.webhooks.repository;

import com.razeef.bugbrother.webhooks.model.GitHubWebhookDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubWebhookDeliveryRepository
        extends JpaRepository<GitHubWebhookDeliveryEntity, String> {
}
