package com.razeef.bugbrother.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.ok(Map.of("authenticated", false));
        }

        String username = auth.getName();
        String avatarUrl = null;

        if (auth.getPrincipal() instanceof OAuth2User oauthUser) {
            Object login = oauthUser.getAttribute("login");
            Object avatar = oauthUser.getAttribute("avatar_url");
            if (login != null) {
                username = login.toString();
            }
            if (avatar != null) {
                avatarUrl = avatar.toString();
            }
        }

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "username", username,
                "avatarUrl", avatarUrl == null ? "" : avatarUrl
        ));
    }
}
