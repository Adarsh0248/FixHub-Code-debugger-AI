package com.razeef.bugbrother.indexes.security;

import com.razeef.bugbrother.indexes.config.InternalApiConfiguration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class InternalWorkerAuthenticationFilter
        extends OncePerRequestFilter {

    public static final String WORKER_KEY_HEADER =
            "X-BugBrother-Worker-Key";

    private final byte[] expectedKey;

    public InternalWorkerAuthenticationFilter(
            InternalApiConfiguration configuration
    ) {
        this.expectedKey = configuration
                .workerKey()
                .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {
        return !request
                .getRequestURI()
                .startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedKey =
                request.getHeader(WORKER_KEY_HEADER);

        boolean valid = suppliedKey != null
                && MessageDigest.isEqual(
                        expectedKey,
                        suppliedKey.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        if (!valid) {
            response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED
            );
            response.setContentType(
                    MediaType.APPLICATION_JSON_VALUE
            );
            response.getWriter().write(
                    """
                    {
                      "error": "INVALID_WORKER_CREDENTIAL",
                      "message": "Valid worker authentication is required"
                    }
                    """
            );
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        "bugbrother-worker",
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_WORKER"
                                )
                        )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}


