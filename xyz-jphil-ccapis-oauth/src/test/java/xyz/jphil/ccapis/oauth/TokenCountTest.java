package xyz.jphil.ccapis.oauth;

import xyz.jphil.ccapis.model.CCAPICredentialOauth;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Test utility demonstrating the SIMPLIFIED token counting API.
 *
 * ✅ This test uses the high-level convenience methods from OAuthApiClient
 *    which automatically handle headers, models, and API complexity.
 *
 * Before (old way - manual header management):
 * <pre>
 * var headers = new HashMap<String, String>();
 * headers.put("anthropic-version", "2023-06-01");
 * headers.put("anthropic-beta", "...");
 * var payload = new HashMap<String, Object>();
 * payload.put("model", "claude-3-5-haiku-20241022");
 * payload.put("messages", messages);
 * var response = apiClient.post(credential, url, payload, headers);
 * // parse JSON manually...
 * </pre>
 *
 * After (new way - simplified API):
 * <pre>
 * var tokenCount = apiClient.countTokens(credential, messages);
 * </pre>
 *
 * Key benefits:
 * - No need to know about anthropic-version or anthropic-beta headers
 * - No need to manually construct payloads or parse responses
 * - No need to remember which model works with OAuth
 * - Model compatibility validated automatically
 * - Headers automatically updated when API changes
 *
 * Usage:
 * - Use first active credential: mvn exec:java -Dexec.mainClass="xyz.jphil.ccapis.oauth.TokenCountTest" -Dexec.classpathScope=test
 * - Use specific credential: mvn exec:java -Dexec.mainClass="xyz.jphil.ccapis.oauth.TokenCountTest" -Dexec.classpathScope=test -Dexec.args="RMN"
 */
public class TokenCountTest {

    public static void main(String[] args) {
        try {
            // Load credentials
            var credManager = new OAuthCredentialsManager();
            var credentials = credManager.load();

            // Get credential ID from command line args, or use first active credential
            String credentialId = (args.length > 0) ? args[0] : null;

            var credential = (credentialId != null)
                    ? credentials.oauthCredentials().stream()
                        .filter(c -> credentialId.equals(c.id()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Credential not found: " + credentialId))
                    : credentials.getActiveOauthCredentials().stream()
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No active OAuth credentials found"));

            System.out.println("Found credential: " + credential.name() + " (" + credential.email() + ")");
            System.out.println("API URL: " + credential.resolvedCcapiApiUrl());
            System.out.println();

            // Create API client and token manager
            var tokenManager = new OAuthTokenManager();

            // Check if we have valid tokens
            if (!tokenManager.hasTokens(credential)) {
                System.err.println("❌ No tokens found for credential: " + credential.id());
                System.err.println("Please run OAuthFlowExample first to complete OAuth authorization.");
                return;
            }

            if (!tokenManager.hasValidTokens(credential)) {
                System.out.println("⚠ Tokens expired, refreshing...");
                var tokens = tokenManager.getTokens(credential);
                tokenManager.refreshAndSaveTokens(credential, tokens);
                System.out.println("✓ Tokens refreshed");
            }

            System.out.println("✓ Valid OAuth tokens found");
            var currentTokens = tokenManager.getTokens(credential);
            System.out.println("Access token: " + currentTokens.accessToken().substring(0, 20) + "...");
            System.out.println();

            var apiClient = new OAuthApiClient(tokenManager);

            // =================================================================
            // TEST 1: Token counting with system prompt (simplified API)
            // =================================================================
            System.out.println("TEST 1: Token counting with system prompt");
            System.out.println("=".repeat(60));

            var messages = List.of(
                    Map.of("role", "user", "content", "What are the key principles of contract law?")
            );

            System.out.println("Calling simplified API:");
            System.out.println("  apiClient.countTokens(credential, systemPrompt, messages)");
            System.out.println();
            System.out.println("Messages: " + messages);
            System.out.println("System prompt: You are a helpful legal assistant.");
            System.out.println();

            // Old way would require:
            // - Building HashMap payload
            // - Adding anthropic-version header
            // - Adding anthropic-beta header
            // - Knowing which model works with OAuth
            // - Manually parsing JSON response
            //
            // New way:
            int tokenCount = apiClient.countTokens(
                credential,
                "You are a helpful legal assistant.",
                messages
            );

            System.out.println("✅ SUCCESS! Input tokens: " + tokenCount);
            System.out.println();

            // =================================================================
            // TEST 2: Simple token counting (no system prompt)
            // =================================================================
            System.out.println("TEST 2: Simple token counting");
            System.out.println("=".repeat(60));

            var simpleMessages = List.of(
                    Map.of("role", "user", "content", "Hello")
            );

            System.out.println("Calling simplified API:");
            System.out.println("  apiClient.countTokens(credential, messages)");
            System.out.println();
            System.out.println("Messages: " + simpleMessages);
            System.out.println();

            // Even simpler - just messages, no system prompt
            int simpleTokenCount = apiClient.countTokens(credential, simpleMessages);

            System.out.println("✅ SUCCESS! Input tokens: " + simpleTokenCount);
            System.out.println();

            // =================================================================
            // SUMMARY
            // =================================================================
            System.out.println("=".repeat(60));
            System.out.println("SUMMARY");
            System.out.println("=".repeat(60));
            System.out.println("✅ All tests passed using simplified API!");
            System.out.println();
            System.out.println("Benefits demonstrated:");
            System.out.println("  - No manual header management");
            System.out.println("  - No manual JSON parsing");
            System.out.println("  - No need to know API URL construction");
            System.out.println("  - No need to remember which model works");
            System.out.println("  - Model compatibility validated automatically");
            System.out.println();
            System.out.println("Code comparison:");
            System.out.println("  Before: ~40 lines of boilerplate");
            System.out.println("  After:  1-2 lines per API call");

            // Cleanup
            apiClient.shutdown();

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
