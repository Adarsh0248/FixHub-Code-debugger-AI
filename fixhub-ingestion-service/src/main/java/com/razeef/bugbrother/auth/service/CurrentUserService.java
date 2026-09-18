package com.razeef.bugbrother.auth.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
   public String requireUserId(){
    Authentication authentication= SecurityContextHolder.getContext().getAuthentication();

    if(authentication==null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())){
        throw new IllegalStateException("Not Authenticated");
    }
     if (!(authentication.getPrincipal() instanceof OAuth2User oauthUser)) {
            throw new IllegalStateException("Authenticated GitHub user is unavailable");
        }

        Object githubId = oauthUser.getAttribute("id");

        if (githubId == null) {
            throw new IllegalStateException("GitHub user ID is unavailable");
        }

        return "github:" + githubId;
   }
}
