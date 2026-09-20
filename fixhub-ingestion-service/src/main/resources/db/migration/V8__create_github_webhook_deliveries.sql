CREATE TABLE github_webhook_deliveries (
    delivery_id VARCHAR(128) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_github_webhook_deliveries_received_at
    ON github_webhook_deliveries (received_at);
