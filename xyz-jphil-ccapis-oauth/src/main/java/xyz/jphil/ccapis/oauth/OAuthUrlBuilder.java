package xyz.jphil.ccapis.oauth;

import xyz.jphil.ccapis.model.CCAPICredentialOauth;
import okhttp3.HttpUrl;

import java.util.List;

/**
 * Builder for OAuth authorization URLs with PKCE support
 */
public class OAuthUrlBuilder {

    private static final String DEFAULT_REDIRECT_URI = "https://console.anthropic.com/oauth/code/callback";
    private static final List<String> DEFAULT_SCOPES = List.of(
            "org:create_api_key",
            "user:profile",
            "user:inference"
    );

    /**
     * Build OAuth authorization URL with PKCE challenge
     *
     * @param credential OAuth credential containing client ID and base URL
     * @param pkceChallenge PKCE challenge containing code challenge
     * @param state optional state parameter for CSRF protection (uses verifier if null)
     * @return complete authorization URL
     */
    public static String buildAuthorizationUrl(
            CCAPICredentialOauth credential,
            PKCEGenerator.PKCEChallenge pkceChallenge,
            String state) {

        var baseUrl = ensureHttpsPrefix(credential.resolvedCcapiBaseUrl());
        var authUrl = baseUrl + "/oauth/authorize";

        // Use verifier as state if not provided
        var stateValue = (state != null && !state.isEmpty()) ? state : pkceChallenge.verifier();

        var url = HttpUrl.parse(authUrl);
        if (url == null) {
            throw new IllegalArgumentException("Invalid authorization URL: " + authUrl);
        }

        var builder = url.newBuilder()
                .addQueryParameter("code", "true")
                .addQueryParameter("client_id", credential.clientId())
                .addQueryParameter("response_type", "code")
                .addQueryParameter("redirect_uri", DEFAULT_REDIRECT_URI)
                .addQueryParameter("scope", String.join(" ", DEFAULT_SCOPES))
                .addQueryParameter("code_challenge", pkceChallenge.challenge())
                .addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("state", stateValue);

        return builder.build().toString();
    }

    /**
     * Build OAuth authorization URL with default state (uses PKCE verifier)
     */
    public static String buildAuthorizationUrl(
            CCAPICredentialOauth credential,
            PKCEGenerator.PKCEChallenge pkceChallenge) {
        return buildAuthorizationUrl(credential, pkceChallenge, null);
    }

    private static String ensureHttpsPrefix(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }
}
