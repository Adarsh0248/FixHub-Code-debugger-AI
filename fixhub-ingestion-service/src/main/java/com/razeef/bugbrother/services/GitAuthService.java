package com.razeef.bugbrother.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;

@Service
public class GitAuthService {

    private final OAuth2AuthorizedClientService clientService;

    @Autowired
    public GitAuthService(OAuth2AuthorizedClientService clientService) {
        this.clientService = clientService;
    }

    public String getGitHubAccessToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new IllegalStateException("Not authenticated");

        OAuth2AuthorizedClient client = clientService.loadAuthorizedClient(
                "github", auth.getName());

        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("GitHub access token not found.");
        }

        return client.getAccessToken().getTokenValue();
    }
}


