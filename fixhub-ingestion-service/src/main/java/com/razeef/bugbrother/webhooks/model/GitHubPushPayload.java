package com.razeef.bugbrother.webhooks.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPushPayload(
        String ref,
        String after,
        boolean deleted,
        Repository repository
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(Long id) {
    }
}
