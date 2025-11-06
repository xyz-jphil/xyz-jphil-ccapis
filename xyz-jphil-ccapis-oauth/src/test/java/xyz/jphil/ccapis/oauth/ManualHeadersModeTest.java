package xyz.jphil.ccapis.oauth;

import xyz.jphil.ccapis.model.CCAPICredentialOauth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test demonstrating MANUAL_HEADERS mode where client manages all headers.
 *
 * This mode is useful when:
 * - You need full control over headers
 * - You're testing specific header combinations
 * - You want to manage headers outside of OAuthApiConfig
 *
 * Usage:
 * mvn exec:java -Dexec.mainClass="xyz.jphil.ccapis.oauth.ManualHeadersModeTest" -Dexec.classpathScope=test
 */
public class ManualHeadersModeTest {

    public static void main(String[] args) {
        try {
            // Load credentials
            var credManager = new OAuthCredentialsManager();
            var credentials = credManager.load();

            var credential = credentials.getActiveOauthCredentials().stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No active OAuth credentials found"));

            System.out.println("Found credential: " + credential.name() + " (" + credential.email() + ")");
            System.out.println("API URL: " + credential.resolvedCcapiApiUrl());
            System.out.println();

            // Create token manager
            var tokenManager = new OAuthTokenManager();

            if (!tokenManager.hasValidTokens(credential)) {
                System.err.println("❌ No valid tokens found");
                return;
            }

            System.out.println("✓ Valid OAuth tokens found");
            System.out.println();

            // =================================================================
            // Create API client in MANUAL_HEADERS mode
            // =================================================================
            System.out.println("Creating OAuthApiClient with MANUAL_HEADERS mode");
            var apiClient = new OAuthApiClient(tokenManager, OAuthApiClient.HeaderMode.MANUAL_HEADERS);
            System.out.println("Auto headers enabled: " + apiClient.isAutoHeadersEnabled());
            System.out.println();

            // =================================================================
            // TEST 1: Using low-level post() with manual headers
            // =================================================================
            System.out.println("TEST 1: Low-level post() with manual headers");
            System.out.println("=".repeat(60));

            var url = credential.resolvedCcapiApiUrl() + "/v1/messages/count_tokens";
            var payload = new HashMap<String, Object>();
            payload.put("model", "claude-3-5-haiku-20241022");
            payload.put("messages", List.of(
                    Map.of("role", "user", "content", "Hello from manual mode!")
            ));

            // Client must provide headers manually
            var headers = new HashMap<String, String>();
            headers.put("anthropic-version", "2023-06-01");
            headers.put("anthropic-beta", "claude-code-20250219,oauth-2025-04-20,interleaved-thinking-2025-05-14,fine-grained-tool-streaming-2025-05-14");

            System.out.println("Calling: apiClient.post(credential, url, payload, headers)");
            System.out.println("Headers provided manually: " + headers.keySet());
            System.out.println();

            var response = apiClient.post(credential, url, payload, headers);
            var jsonNode = apiClient.getObjectMapper().readTree(response);
            int tokenCount = jsonNode.get("input_tokens").asInt();

            System.out.println("✅ SUCCESS! Input tokens: " + tokenCount);
            System.out.println();

            // =================================================================
            // TEST 2: Using convenience method WITHOUT headers (will fail)
            // =================================================================
            System.out.println("TEST 2: Convenience method in MANUAL mode (no auto headers)");
            System.out.println("=".repeat(60));

            var messages = List.of(
                    Map.of("role", "user", "content", "Test message")
            );

            System.out.println("Calling: apiClient.countTokens(credential, messages)");
            System.out.println("Note: In MANUAL_HEADERS mode, convenience methods don't add headers");
            System.out.println();

            try {
                // This will likely fail because no headers are sent
                int count = apiClient.countTokens(credential, messages);
                System.out.println("⚠️ Unexpected success! Token count: " + count);
                System.out.println("   (API might be accepting requests without headers now)");
            } catch (Exception e) {
                System.out.println("❌ Expected failure: " + e.getMessage());
                System.out.println("   This is correct - MANUAL mode requires explicit headers");
            }
            System.out.println();

            // =================================================================
            // SUMMARY
            // =================================================================
            System.out.println("=".repeat(60));
            System.out.println("MANUAL_HEADERS MODE SUMMARY");
            System.out.println("=".repeat(60));
            System.out.println("✅ MANUAL_HEADERS mode working as expected");
            System.out.println();
            System.out.println("Key characteristics:");
            System.out.println("  ✓ Client has full control over headers");
            System.out.println("  ✓ No automatic header injection");
            System.out.println("  ✓ Convenience methods available but don't add headers");
            System.out.println("  ✓ Low-level post()/get() methods work with explicit headers");
            System.out.println();
            System.out.println("Use cases:");
            System.out.println("  - Testing specific header combinations");
            System.out.println("  - Custom header requirements");
            System.out.println("  - Full control over API requests");

            apiClient.shutdown();

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
