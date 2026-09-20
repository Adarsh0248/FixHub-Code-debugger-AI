package com.razeef.bugbrother.webhooks.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class GitHubWebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";
    private final byte[] secret;

    public GitHubWebhookSignatureVerifier(
            @Value("${bugbrother.github.webhook-secret:}")
            String secret
    ) {
        this.secret = secret == null
                ? new byte[0]
                : secret.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(byte[] body, String suppliedSignature) {
        if (secret.length == 0) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "GitHub webhook secret is not configured"
            );
        }
        if (body == null
                || suppliedSignature == null
                || !suppliedSignature.startsWith(PREFIX)) {
            reject();
        }

        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(
                    suppliedSignature.substring(PREFIX.length())
            );
        } catch (IllegalArgumentException exception) {
            reject();
            return;
        }

        if (!MessageDigest.isEqual(sign(body), supplied)) {
            reject();
        }
    }

    private byte[] sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "HMAC-SHA256 is unavailable",
                    exception
            );
        }
    }

    private void reject() {
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid GitHub webhook signature"
        );
    }
}
