package xyz.jphil.ccapis.oauth.initial_attempts;

import xyz.jphil.ccapis.model.CCAPIsCredentials;
import xyz.jphil.ccapis.oauth.*;
import org.junit.Test;

import java.util.Scanner;

/**
 * ⚠️ INCOMPLETE/MISLEADING TEST - DO NOT USE AS REFERENCE
 *
 * This test was an initial attempt that calls the /v1/messages API WITHOUT required headers.
 * It will likely FAIL or give unexpected results.
 *
 * PROBLEM: Line 111 calls apiClient.post() without the required anthropic-beta headers
 * that are MANDATORY for OAuth authentication with the Messages API.
 *
 * ✅ For CORRECT working example, see: xyz.jphil.ccapis.oauth.examples.OAuthFlowExample
 *
 * Required headers for /v1/messages with OAuth:
 * - anthropic-version: 2023-06-01
 * - anthropic-beta: claude-code-20250219,oauth-2025-04-20,...
 *
 * This file is kept in initial_attempts package for historical reference only.
 *
 * Original test flow (incomplete):
 * 1. Load OAuth credential from CCAPIsCredentials.xml
 * 2. Generate PKCE challenge
 * 3. Build authorization URL
 * 4. User opens URL in browser and completes authorization
 * 5. User pastes authorization code back
 * 6. Exchange code for tokens
 * 7. Save tokens
 * 8. Test API call with Bearer token ❌ WITHOUT REQUIRED HEADERS
 * 9. Test token refresh
 */
public class OAuthFlowBasicTest {
	
	public static void main(String[]args) throws Exception {
		new OAuthFlowBasicTest().testCompleteOAuthFlow();
	}

    @Test
    public void testCompleteOAuthFlow() throws Exception {
        System.out.println("=== OAuth Flow Test ===\n");

        // 1. Load OAuth credential
        var credentials = loadOAuthCredentials();
        if (credentials.oauthCredentials().isEmpty()) {
            System.err.println("No OAuth credentials found in CCAPIsCredentials.xml");
            System.err.println("Please add CCAPICredentialOauth entries to the credentials file");
            return;
        }

        // Use first active credential
        var credential = credentials.getActiveOauthCredentials().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No active OAuth credentials found"));

        System.out.println("Using credential: " + credential.name() + " (ID: " + credential.id() + ")");
        System.out.println("Client ID: " + credential.clientId());
        System.out.println();

        // 2. Generate PKCE challenge
        var pkce = PKCEGenerator.generate();
        System.out.println("Generated PKCE verifier: " + pkce.verifier());
        System.out.println("Generated PKCE challenge: " + pkce.challenge());
        System.out.println();

        // 3. Build authorization URL
        var authUrl = OAuthUrlBuilder.buildAuthorizationUrl(credential, pkce);
        System.out.println("Authorization URL:");
        System.out.println(authUrl);
        System.out.println();

        // 4. User opens URL and completes authorization
        System.out.println("Please:");
        System.out.println("1. Open the URL above in your browser");
        System.out.println("2. Complete the authorization");
        System.out.println("3. Copy the authorization code from the callback");
        System.out.println("4. Paste it below");
        System.out.println();

        System.out.print("Enter authorization code: ");
        System.out.flush(); // Ensure prompt is displayed
        var scanner = new Scanner(System.in);
        var authCode = scanner.nextLine().trim();
        System.out.println("Authorization code received (length: " + authCode.length() + ")"); // Confirm input received

        if (authCode.isEmpty()) {
            System.err.println("No authorization code provided, test stopped");
            return;
        }

        System.out.println();

        // 5. Exchange code for tokens
        var oauthClient = new OAuthClient();
        System.out.println("Exchanging authorization code for tokens...");

        try {
            var tokens = oauthClient.exchangeCodeForTokens(credential, authCode, pkce.verifier());
            System.out.println("✓ Successfully obtained tokens");
            System.out.println("  Access token: " + tokens.accessToken().substring(0, 20) + "...");
            System.out.println("  Refresh token: " + tokens.refreshToken().substring(0, 20) + "...");
            System.out.println("  Expires at: " + tokens.expiresAt() + " (epoch seconds)");
            System.out.println();

            // 6. Save tokens
            var tokenManager = new OAuthTokenManager(oauthClient, new OAuthTokenStorage());
            tokenManager.saveTokens(credential, tokens);
            System.out.println("✓ Tokens saved to: " + tokenManager.getTokenStorage().getStorageDir());
            System.out.println();

            // 7. Test API call with Bearer token
            System.out.println("Testing API call with Bearer token...");
            var apiClient = new OAuthApiClient(tokenManager);

            var apiUrl = credential.resolvedCcapiApiUrl() + "/v1/messages";
            System.out.println("API endpoint: " + apiUrl);

            // ❌ PROBLEM: Simple test request WITHOUT REQUIRED HEADERS
            // This will likely fail or give authentication errors
            try {
                var testPayload = new java.util.HashMap<String, Object>();
                testPayload.put("model", "claude-3-5-sonnet-20241022");
                testPayload.put("max_tokens", 100);
                testPayload.put("messages", java.util.List.of(
                        java.util.Map.of("role", "user", "content", "Hello")
                ));

                // ❌ MISSING REQUIRED HEADERS - This is the problem!
                var response = apiClient.post(credential, apiUrl, testPayload);
                System.out.println("✓ API call successful");
                System.out.println("Response: " + response.substring(0, Math.min(200, response.length())) + "...");
            } catch (Exception e) {
                System.err.println("✗ API call failed (this might be expected if OAuth app doesn't have inference permissions)");
                System.err.println("  Error: " + e.getMessage());
            }

            System.out.println();

            // 8. Verify token can be loaded
            System.out.println("Verifying token persistence...");
            var loadedTokens = tokenManager.getTokens(credential);
            if (loadedTokens != null) {
                System.out.println("✓ Tokens successfully loaded from storage");
                System.out.println("  Access token matches: " + loadedTokens.accessToken().equals(tokens.accessToken()));
            }

            System.out.println();
            System.out.println("=== OAuth Flow Test Complete ===");

        } catch (Exception e) {
            System.err.println("✗ OAuth flow failed");
            e.printStackTrace();
            throw e;
        } finally {
            oauthClient.shutdown();
        }
    }

    /**
     * Load OAuth credentials from default location
     */
    private CCAPIsCredentials loadOAuthCredentials() throws Exception {
        var credManager = new OAuthCredentialsManager();
        return credManager.load();
    }
}
