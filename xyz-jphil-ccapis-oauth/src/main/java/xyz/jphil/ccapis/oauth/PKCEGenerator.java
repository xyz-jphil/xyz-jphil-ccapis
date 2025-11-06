package xyz.jphil.ccapis.oauth;

import lombok.Data;
import lombok.experimental.Accessors;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE (Proof Key for Code Exchange) generator for OAuth 2.0.
 * Implements RFC 7636 for enhanced security in OAuth flows.
 */
public class PKCEGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int VERIFIER_LENGTH = 32; // 32 bytes = 256 bits

    /**
     * Generate a PKCE code verifier and challenge pair
     */
    public static PKCEChallenge generate() {
        var verifier = generateCodeVerifier();
        var challenge = generateCodeChallenge(verifier);
        return new PKCEChallenge(verifier, challenge);
    }

    /**
     * Generate a random code verifier (base64url encoded random bytes)
     */
    private static String generateCodeVerifier() {
        var randomBytes = new byte[VERIFIER_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return base64UrlEncode(randomBytes);
    }

    /**
     * Generate code challenge from verifier using S256 method (SHA-256 hash)
     */
    private static String generateCodeChallenge(String verifier) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return base64UrlEncode(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Base64 URL encode without padding (RFC 7636 compliant)
     */
    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * PKCE challenge data containing verifier and challenge
     */
    @Data
    @Accessors(fluent = true, chain = true)
    public static class PKCEChallenge {
        private final String verifier;
        private final String challenge;

        public PKCEChallenge(String verifier, String challenge) {
            this.verifier = verifier;
            this.challenge = challenge;
        }
    }
}
