package com.razeef.bugbrother.webhooks.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "github_webhook_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GitHubWebhookDeliveryEntity {

    @Id
    @Column(name = "delivery_id", nullable = false, length = 128)
    private String deliveryId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public GitHubWebhookDeliveryEntity(
            String deliveryId,
            String eventType
    ) {
        this.deliveryId = deliveryId;
        this.eventType = eventType;
        this.receivedAt = Instant.now();
    }
}
