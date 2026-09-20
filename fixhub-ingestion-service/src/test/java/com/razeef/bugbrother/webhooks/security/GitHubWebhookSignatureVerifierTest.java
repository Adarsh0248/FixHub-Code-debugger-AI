package com.razeef.bugbrother.webhooks.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubWebhookSignatureVerifierTest {

    @Test
    void acceptsMatchingSignatureAndRejectsChangedBody() throws Exception {
        String secret = "test-webhook-secret";
        byte[] body = "{\"ref\":\"refs/heads/main\"}"
                .getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + HexFormat.of().formatHex(
                hmac(secret, body)
        );

        GitHubWebhookSignatureVerifier verifier =
                new GitHubWebhookSignatureVerifier(secret);

        assertDoesNotThrow(() -> verifier.verify(body, signature));
        assertThrows(
                ResponseStatusException.class,
                () -> verifier.verify(
                        "changed".getBytes(StandardCharsets.UTF_8),
                        signature
                )
        );
    }

    private byte[] hmac(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));
        return mac.doFinal(body);
    }
}
