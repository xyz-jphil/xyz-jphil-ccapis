package xyz.jphil.ccapis.oauth.examples;

import xyz.jphil.ccapis.model.CCAPICredentialOauth;
import xyz.jphil.ccapis.oauth.*;

import java.util.Scanner;

/**
 * Example demonstrating the complete OAuth flow with SIMPLIFIED API.
 *
 * ✅ This example now uses the high-level convenience methods from OAuthApiClient
 *    which automatically handle headers, models, and API complexity.
 *
 * This example shows:
 * 1. Loading OAuth credentials
 * 2. Generating authorization URL
 * 3. Exchanging authorization code for tokens
 * 4. Making authenticated API calls using SIMPLIFIED methods (no manual headers!)
 * 5. Token refresh
 *
 * Key improvement:
 * - Before: Manual header management with anthropic-version and anthropic-beta
 * - After: Simple one-liner API calls with automatic header injection
 *
 * Run with: mvn exec:java -Dexec.mainClass="xyz.jphil.ccapis.oauth.examples.OAuthFlowExample"
 */
public class OAuthFlowExample {

    public static void main(String[] args) {
        try {
            // Step 1: Load OAuth credentials
            var credManager = new OAuthCredentialsManager();
            var credentials = credManager.load();

            if (credentials.oauthCredentials().isEmpty()) {
                System.err.println("No OAuth credentials found!");
                System.err.println("Please add CCAPICredentialOauth entries to:");
                System.err.println(credManager.getCredentialsPath());
                return;
            }

            // Get first active credential
            var credential = credentials.getActiveOauthCredentials().stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No active OAuth credentials"));

            System.out.println("Using credential: " + credential.name());
            System.out.println("Client ID: " + credential.clientId());
            System.out.println();

            // Check if we already have tokens
            var tokenManager = new OAuthTokenManager();
            if (tokenManager.hasTokens(credential)) {
                System.out.println("✓ Tokens already exist for this credential");

                if (tokenManager.hasValidTokens(credential)) {
                    System.out.println("✓ Tokens are still valid");
                    demonstrateApiCall(credential, tokenManager);
                } else {
                    System.out.println("⚠ Tokens are expired, refreshing...");
                    var tokens = tokenManager.getTokens(credential);
                    var newTokens = tokenManager.refreshAndSaveTokens(credential, tokens);
                    System.out.println("✓ Tokens refreshed successfully");
                    demonstrateApiCall(credential, tokenManager);
                }
                return;
            }

            // Step 2: Generate PKCE and authorization URL
            System.out.println("No tokens found. Starting OAuth flow...");
            var pkce = PKCEGenerator.generate();
            var authUrl = OAuthUrlBuilder.buildAuthorizationUrl(credential, pkce);

            System.out.println("\nAuthorization URL:");
            System.out.println(authUrl);
            System.out.println();
            System.out.println("Please:");
            System.out.println("1. Open the URL above in your browser");
            System.out.println("2. Complete the authorization");
            System.out.println("3. Copy the authorization code from the callback");
            System.out.println("4. Paste it below");
            System.out.println();

            // Step 3: Get authorization code from user
            System.out.print("Enter authorization code: ");
            var scanner = new Scanner(System.in);
            var authCode = scanner.nextLine().trim();

            if (authCode.isEmpty()) {
                System.err.println("No authorization code provided");
                return;
            }

            // Step 4: Exchange code for tokens
            var oauthClient = new OAuthClient();
            System.out.println("\nExchanging code for tokens...");
            var tokens = oauthClient.exchangeCodeForTokens(credential, authCode, pkce.verifier());
            System.out.println("✓ Tokens obtained successfully");

            // Step 5: Save tokens
            tokenManager.saveTokens(credential, tokens);
            System.out.println("✓ Tokens saved to: " + tokenManager.getTokenStorage().getStorageDir());

            // Step 6: Demonstrate API call
            demonstrateApiCall(credential, tokenManager);

            oauthClient.shutdown();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void demonstrateApiCall(
            CCAPICredentialOauth credential,
            OAuthTokenManager tokenManager) {

        try {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("DEMONSTRATING SIMPLIFIED API");
            System.out.println("=".repeat(60));

            var apiClient = new OAuthApiClient(tokenManager);

            // =================================================================
            // TEST 1: Simple Messages API call (no system prompt)
            // =================================================================
            System.out.println("\nTEST 1: Messages API - Simple call");
            System.out.println("-".repeat(60));

            var messages = java.util.List.of(
                    java.util.Map.of("role", "user", "content", "Say hello in one word")
            );

            System.out.println("Calling: apiClient.callMessages(credential, messages)");
            System.out.println();

            // Before: ~15 lines of code with manual headers
            // After: Just one line!
            var response = apiClient.callMessages(credential, messages);

            System.out.println("✓ API call successful");
            System.out.println("Response preview: " + response.substring(0, Math.min(200, response.length())) + "...");
            System.out.println();

            // =================================================================
            // TEST 2: Messages API with system prompt
            // =================================================================
            System.out.println("TEST 2: Messages API - With system prompt");
            System.out.println("-".repeat(60));

            var messages2 = java.util.List.of(
                    java.util.Map.of("role", "user", "content", "What is 2+2?")
            );

            System.out.println("Calling: apiClient.callMessages(credential, systemPrompt, messages)");
            System.out.println();

            var response2 = apiClient.callMessages(
                credential,
                "You are a helpful math tutor. Be concise.",
                messages2
            );

            System.out.println("✓ API call successful");
            System.out.println("Response preview: " + response2.substring(0, Math.min(200, response2.length())) + "...");
            System.out.println();

            // =================================================================
            // TEST 3: Token counting (doesn't consume quota!)
            // =================================================================
            System.out.println("TEST 3: Token counting");
            System.out.println("-".repeat(60));

            var testMessages = java.util.List.of(
                    java.util.Map.of("role", "user", "content", "How are you today?")
            );

            System.out.println("Calling: apiClient.countTokens(credential, systemPrompt, messages)");
            System.out.println();

            int tokenCount = apiClient.countTokens(
                credential,
                "You are a friendly assistant.",
                testMessages
            );

            System.out.println("✓ Token count: " + tokenCount + " tokens");
            System.out.println();

            // =================================================================
            // SUMMARY
            // =================================================================
            System.out.println("=".repeat(60));
            System.out.println("SIMPLIFIED API SUMMARY");
            System.out.println("=".repeat(60));
            System.out.println("✅ All API calls successful!");
            System.out.println();
            System.out.println("What changed:");
            System.out.println("  ❌ Before: Manual header construction (anthropic-version, anthropic-beta)");
            System.out.println("  ❌ Before: Manual payload building");
            System.out.println("  ❌ Before: URL construction");
            System.out.println("  ❌ Before: JSON parsing");
            System.out.println();
            System.out.println("  ✅ After: Single method call!");
            System.out.println("  ✅ After: Automatic header injection");
            System.out.println("  ✅ After: Model compatibility validation");
            System.out.println("  ✅ After: Future-proof (headers in OAuthApiConfig)");

            apiClient.shutdown();

        } catch (Exception e) {
            System.err.println("✗ API call failed: " + e.getMessage());
            System.err.println("This might be expected if OAuth app doesn't have inference permissions");
            e.printStackTrace();
        }
    }
}
