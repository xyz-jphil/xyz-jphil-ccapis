package xyz.jphil.ccapis.oauth;

import xyz.jphil.ccapis.model.CCAPICredentialOauth;

import java.io.IOException;

/**
 * Manages OAuth tokens with automatic expiration checking and refresh.
 * Handles token persistence and ensures tokens are always valid.
 */
public class OAuthTokenManager {

    private final OAuthClient oauthClient;
    private final OAuthTokenStorage tokenStorage;

    public OAuthTokenManager() {
        this.oauthClient = new OAuthClient();
        this.tokenStorage = new OAuthTokenStorage();
    }

    public OAuthTokenManager(OAuthClient oauthClient, OAuthTokenStorage tokenStorage) {
        this.oauthClient = oauthClient;
        this.tokenStorage = tokenStorage;
    }

    /**
     * Get valid access token for a credential, refreshing if necessary
     *
     * @param credential OAuth credential
     * @return valid access token
     * @throws IOException if token retrieval or refresh fails
     */
    public String getValidAccessToken(CCAPICredentialOauth credential) throws IOException {
        var tokens = tokenStorage.load(credential.id());

        if (tokens == null) {
            throw new IOException("No tokens found for credential: " + credential.id() +
                    ". Please complete OAuth authorization flow first." +
                    "\n  Run: mvn exec:java -Dexec.mainClass=\"xyz.jphil.ccapis.oauth.examples.OAuthFlowExample\"");
        }

        // Check if token is expired and refresh if needed
        if (tokens.isExpired()) {
            try {
                tokens = refreshAndSaveTokens(credential, tokens);
            } catch (IOException e) {
                // If refresh fails, likely the refresh token is expired/invalid
                throw new IOException("Token refresh failed for credential: " + credential.id() +
                        ". Your refresh token may be expired or invalid." +
                        "\n  Please re-authenticate using:" +
                        "\n  mvn exec:java -Dexec.mainClass=\"xyz.jphil.ccapis.oauth.examples.OAuthFlowExample\"" +
                        "\n  Original error: " + e.getMessage(), e);
            }
        }

        return tokens.accessToken();
    }

    /**
     * Get OAuth tokens for a credential
     *
     * @param credential OAuth credential
     * @return OAuth tokens or null if not found
     * @throws IOException if load fails
     */
    public OAuthTokens getTokens(CCAPICredentialOauth credential) throws IOException {
        return tokenStorage.load(credential.id());
    }

    /**
     * Save OAuth tokens after authorization code exchange
     *
     * @param credential OAuth credential
     * @param tokens OAuth tokens to save
     * @throws IOException if save fails
     */
    public void saveTokens(CCAPICredentialOauth credential, OAuthTokens tokens) throws IOException {
        tokenStorage.save(credential.id(), tokens);
    }

    /**
     * Refresh tokens and save the new ones
     *
     * @param credential OAuth credential
     * @param currentTokens current OAuth tokens with refresh token
     * @return new OAuth tokens
     * @throws IOException if refresh or save fails
     */
    public OAuthTokens refreshAndSaveTokens(
            CCAPICredentialOauth credential,
            OAuthTokens currentTokens) throws IOException {

        if (currentTokens.refreshToken() == null || currentTokens.refreshToken().isEmpty()) {
            throw new IOException("No refresh token available for credential: " + credential.id());
        }

        var newTokens = oauthClient.refreshTokens(credential, currentTokens.refreshToken());
        tokenStorage.save(credential.id(), newTokens);
        return newTokens;
    }

    /**
     * Delete stored tokens for a credential
     *
     * @param credential OAuth credential
     * @throws IOException if delete fails
     */
    public void deleteTokens(CCAPICredentialOauth credential) throws IOException {
        tokenStorage.delete(credential.id());
    }

    /**
     * Check if tokens exist for a credential
     *
     * @param credential OAuth credential
     * @return true if tokens exist
     */
    public boolean hasTokens(CCAPICredentialOauth credential) {
        return tokenStorage.exists(credential.id());
    }

    /**
     * Check if tokens are valid (exist and not expired)
     *
     * @param credential OAuth credential
     * @return true if tokens are valid
     * @throws IOException if load fails
     */
    public boolean hasValidTokens(CCAPICredentialOauth credential) throws IOException {
        var tokens = tokenStorage.load(credential.id());
        return tokens != null && !tokens.isExpired();
    }

    /**
     * Get the OAuth client for direct access
     */
    public OAuthClient getOAuthClient() {
        return oauthClient;
    }

    /**
     * Get the token storage for direct access
     */
    public OAuthTokenStorage getTokenStorage() {
        return tokenStorage;
    }

    /**
     * Shutdown the OAuth client and release resources
     */
    public void shutdown() {
        oauthClient.shutdown();
    }
}
