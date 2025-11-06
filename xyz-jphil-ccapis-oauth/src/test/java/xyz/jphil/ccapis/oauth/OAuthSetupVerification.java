package xyz.jphil.ccapis.oauth;

import xyz.jphil.ccapis.model.CCAPICredentialOauth;

/**
 * Non-interactive OAuth setup verification.
 * Verifies that OAuth credentials can be loaded and authorization URLs can be generated.
 */
public class OAuthSetupVerification {

    public static void main(String[] args) {
        System.out.println("=== OAuth Module Setup Verification ===\n");

        try {
            // Step 1: Load OAuth credentials
            System.out.println("Step 1: Loading OAuth credentials...");
            var credManager = new OAuthCredentialsManager();
            var credentials = credManager.load();

            System.out.println("✓ Credentials file loaded: " + credManager.getCredentialsPath());
            System.out.println("  Regular credentials: " + credentials.credentials().size());
            System.out.println("  OAuth credentials: " + credentials.oauthCredentials().size());
            System.out.println();

            if (credentials.oauthCredentials().isEmpty()) {
                System.err.println("✗ No OAuth credentials found!");
                System.err.println("Please add CCAPICredentialOauth entries to:");
                System.err.println(credManager.getCredentialsPath());
                System.exit(1);
            }

            // Step 2: Display OAuth credentials
            System.out.println("Step 2: OAuth Credentials Found:");
            for (var cred : credentials.oauthCredentials()) {
                System.out.println("  • " + cred.id() + " - " + cred.name());
                System.out.println("    Email: " + cred.email());
                System.out.println("    Client ID: " + cred.clientId());
                System.out.println("    Active: " + cred.active());
                System.out.println("    Track Usage: " + cred.trackUsage());
                System.out.println("    Base URL (raw): " + cred.ccapiBaseUrl());
                System.out.println("    Base URL (resolved): " + cred.resolvedCcapiBaseUrl());
                System.out.println("    Console URL (resolved): " + cred.resolvedCcapiConsoleUrl());
                System.out.println("    API URL (resolved): " + cred.resolvedCcapiApiUrl());
                System.out.println();
            }

            // Step 3: Test PKCE generation
            System.out.println("Step 3: Testing PKCE generation...");
            var pkce = PKCEGenerator.generate();
            System.out.println("✓ PKCE generated successfully");
            System.out.println("  Verifier: " + pkce.verifier());
            System.out.println("  Challenge: " + pkce.challenge());
            System.out.println("  Challenge Method: S256");
            System.out.println();

            // Step 4: Generate authorization URL for first credential
            var firstCred = credentials.getActiveOauthCredentials().stream()
                    .findFirst()
                    .orElse(credentials.oauthCredentials().get(0));

            System.out.println("Step 4: Generating OAuth authorization URL...");
            var authUrl = OAuthUrlBuilder.buildAuthorizationUrl(firstCred, pkce);
            System.out.println("✓ Authorization URL generated");
            System.out.println();
            System.out.println("Credential: " + firstCred.name() + " (" + firstCred.id() + ")");
            System.out.println("Authorization URL:");
            System.out.println(authUrl);
            System.out.println();

            // Step 5: Verify token storage location
            System.out.println("Step 5: Token storage configuration...");
            var tokenStorage = new OAuthTokenStorage();
            System.out.println("✓ Token storage directory: " + tokenStorage.getStorageDir());
            System.out.println("  Tokens will be saved as: <credential-id>.tokens.json");
            System.out.println();

            // Step 6: Check if tokens already exist
            System.out.println("Step 6: Checking for existing tokens...");
            var tokenManager = new OAuthTokenManager();
            for (var cred : credentials.oauthCredentials()) {
                boolean hasTokens = tokenManager.hasTokens(cred);
                System.out.println("  " + cred.id() + ": " + (hasTokens ? "✓ Tokens exist" : "✗ No tokens"));

                if (hasTokens) {
                    try {
                        boolean isValid = tokenManager.hasValidTokens(cred);
                        System.out.println("       " + (isValid ? "✓ Tokens are valid" : "⚠ Tokens expired"));
                    } catch (Exception e) {
                        System.out.println("       ⚠ Error checking validity: " + e.getMessage());
                    }
                }
            }
            System.out.println();

            // Summary
            System.out.println("=== Verification Complete ===");
            System.out.println("✓ OAuth credentials loading: WORKING");
            System.out.println("✓ PKCE generation: WORKING");
            System.out.println("✓ Authorization URL building: WORKING");
            System.out.println("✓ Token storage setup: WORKING");
            System.out.println();
            System.out.println("Next Steps:");
            System.out.println("1. Open the authorization URL in your browser");
            System.out.println("2. Complete the OAuth authorization");
            System.out.println("3. Get the authorization code from the callback");
            System.out.println("4. Run the interactive flow to exchange code for tokens");
            System.out.println();
            System.out.println("For interactive flow, run:");
            System.out.println("  mvn test -Dtest=OAuthFlowBasicTest -pl xyz-jphil-ccapis-oauth");

        } catch (Exception e) {
            System.err.println("✗ Verification failed!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
