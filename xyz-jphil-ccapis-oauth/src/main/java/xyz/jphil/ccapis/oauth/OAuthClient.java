package xyz.jphil.ccapis.oauth;

import xyz.jphil.ccapis.model.CCAPICredentialOauth;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * OAuth 2.0 client for CCAPI with PKCE support.
 * Handles authorization code exchange and token refresh.
 */
public class OAuthClient {

    private static final String DEFAULT_REDIRECT_URI = "https://console.anthropic.com/oauth/code/callback";
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OAuthClient() {
        this.httpClient = createHttpClient();
        this.objectMapper = createObjectMapper();
    }

    private OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    private ObjectMapper createObjectMapper() {
        var mapper = new ObjectMapper();
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );
        return mapper;
    }

    /**
     * Exchange authorization code for access and refresh tokens
     *
     * @param credential OAuth credential
     * @param code authorization code from OAuth callback
     * @param verifier PKCE code verifier
     * @return OAuth tokens
     * @throws IOException if exchange fails
     */
    public OAuthTokens exchangeCodeForTokens(
            CCAPICredentialOauth credential,
            String code,
            String verifier) throws IOException {

        var tokenUrl = credential.resolvedCcapiConsoleUrl() + "/v1/oauth/token";

        // Parse code which may contain state (code#state format from Anthropic)
        var codeParts = code.split("#");
        var actualCode = codeParts[0];
        var state = codeParts.length > 1 ? codeParts[1] : "";

        // Build request payload
        var payload = new java.util.HashMap<String, String>();
        payload.put("code", actualCode);
        payload.put("state", state);
        payload.put("grant_type", "authorization_code");
        payload.put("client_id", credential.clientId());
        payload.put("redirect_uri", DEFAULT_REDIRECT_URI);
        payload.put("code_verifier", verifier);

        var jsonPayload = objectMapper.writeValueAsString(payload);
        var requestBody = RequestBody.create(jsonPayload, JSON);

        var request = new Request.Builder()
                .url(tokenUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                var errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Token exchange failed: HTTP " + response.code() + " - " + errorBody);
            }

            var responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Empty response body from token endpoint");
            }

            var tokens = objectMapper.readValue(responseBody.string(), OAuthTokens.class);
            tokens.calculateExpiresAt(); // Calculate expiration timestamp
            return tokens;
        }
    }

    /**
     * Refresh access token using refresh token
     *
     * @param credential OAuth credential
     * @param refreshToken refresh token from previous token response
     * @return new OAuth tokens
     * @throws IOException if refresh fails
     */
    public OAuthTokens refreshTokens(
            CCAPICredentialOauth credential,
            String refreshToken) throws IOException {

        var tokenUrl = credential.resolvedCcapiConsoleUrl() + "/v1/oauth/token";

        // Build request payload
        var payload = new java.util.HashMap<String, String>();
        payload.put("grant_type", "refresh_token");
        payload.put("refresh_token", refreshToken);
        payload.put("client_id", credential.clientId());

        var jsonPayload = objectMapper.writeValueAsString(payload);
        var requestBody = RequestBody.create(jsonPayload, JSON);

        var request = new Request.Builder()
                .url(tokenUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                var errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Token refresh failed: HTTP " + response.code() + " - " + errorBody);
            }

            var responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Empty response body from token endpoint");
            }

            var tokens = objectMapper.readValue(responseBody.string(), OAuthTokens.class);
            tokens.calculateExpiresAt(); // Calculate expiration timestamp
            return tokens;
        }
    }

    /**
     * Shutdown the HTTP client and release resources
     */
    public void shutdown() {
        httpClient.dispatcher().cancelAll();
        httpClient.connectionPool().evictAll();
        httpClient.dispatcher().executorService().shutdownNow();
    }
}
