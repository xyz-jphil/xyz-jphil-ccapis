package xyz.jphil.ccapis.oauth.utils;

import xyz.jphil.ccapis.model.CCAPICredentialOauth;
import xyz.jphil.ccapis.oauth.*;

/**
 * Utility to exchange authorization code for tokens.
 * Usage: TokenExchangeUtil <credential-id> <authorization-code> <pkce-verifier>
 *
 * This is a command-line utility for manual token exchange operations.
 */
public class TokenExchangeUtil {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: TokenExchangeUtil <credential-id> <auth-code> <pkce-verifier>");
            System.err.println();
            System.err.println("Example:");
            System.err.println("  TokenExchangeUtil RMN \"code123#state456\" \"abc123...\"");
            System.exit(1);
        }

        var credentialId = args[0];
        var authCode = args[1];
        var verifier = args[2];

        System.out.println("=== OAuth Token Exchange ===\n");

        try {
            // Load credentials
            System.out.println("Loading credential: " + credentialId);
            var credManager = new OAuthCredentialsManager();
            var credentials = credManager.load();

            var credential = credentials.getOauthById(credentialId);
            if (credential == null) {
                System.err.println("✗ Credential not found: " + credentialId);
                System.exit(1);
            }

            System.out.println("✓ Found credential: " + credential.name() + " (" + credential.email() + ")");
            System.out.println();

            // Exchange code for tokens
            System.out.println("Exchanging authorization code for tokens...");
            System.out.println("  Code: " + authCode.substring(0, Math.min(20, authCode.length())) + "...");
            System.out.println("  Verifier: " + verifier.substring(0, Math.min(20, verifier.length())) + "...");
            System.out.println();

            var oauthClient = new OAuthClient();
            var tokens = oauthClient.exchangeCodeForTokens(credential, authCode, verifier);

            System.out.println("✓ Token exchange successful!");
            System.out.println("  Access Token: " + tokens.accessToken().substring(0, 20) + "...");
            System.out.println("  Refresh Token: " + tokens.refreshToken().substring(0, 20) + "...");
            System.out.println("  Expires At: " + tokens.expiresAt() + " (epoch seconds)");
            System.out.println("  Token Type: " + tokens.tokenType());
            System.out.println();

            // Save tokens
            System.out.println("Saving tokens...");
            var tokenManager = new OAuthTokenManager();
            tokenManager.saveTokens(credential, tokens);

            System.out.println("✓ Tokens saved to: " + tokenManager.getTokenStorage().getStorageDir());
            System.out.println("  File: " + credentialId + ".tokens.json");
            System.out.println();

            System.out.println("=== OAuth Setup Complete ===");
            System.out.println("You can now use the OAuth API client to make authenticated requests!");

            oauthClient.shutdown();

        } catch (Exception e) {
            System.err.println("✗ Token exchange failed!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
