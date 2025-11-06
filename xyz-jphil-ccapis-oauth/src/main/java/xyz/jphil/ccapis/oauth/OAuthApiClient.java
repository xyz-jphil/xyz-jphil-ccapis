package xyz.jphil.ccapis.oauth;

import xyz.jphil.ccapis.model.CCAPICredentialOauth;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * API client for making authenticated requests using OAuth Bearer tokens.
 * Automatically handles token refresh when tokens expire.
 *
 * Configuration modes:
 * - AUTO_HEADERS (default): Convenience methods automatically add headers from OAuthApiConfig
 * - MANUAL_HEADERS: No automatic headers, client must provide all headers explicitly
 */
public class OAuthApiClient {

    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36";
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OAuthTokenManager tokenManager;
    private final boolean autoHeaders;

    /**
     * Header management mode for convenience methods
     */
    public enum HeaderMode {
        /**
         * Automatically add headers from OAuthApiConfig (default).
         * Best for clients who don't want to manage API headers.
         */
        AUTO_HEADERS,

        /**
         * No automatic headers - client must provide all headers.
         * Best for clients who want full control over headers.
         */
        MANUAL_HEADERS
    }

    /**
     * Create OAuth API client with default token manager and AUTO_HEADERS mode
     */
    public OAuthApiClient() {
        this(new OAuthTokenManager(), HeaderMode.AUTO_HEADERS);
    }

    /**
     * Create OAuth API client with custom token manager and AUTO_HEADERS mode
     */
    public OAuthApiClient(OAuthTokenManager tokenManager) {
        this(tokenManager, HeaderMode.AUTO_HEADERS);
    }

    /**
     * Create OAuth API client with custom token manager and header mode
     *
     * @param tokenManager Token manager for handling OAuth tokens
     * @param headerMode Header management mode (AUTO_HEADERS or MANUAL_HEADERS)
     */
    public OAuthApiClient(OAuthTokenManager tokenManager, HeaderMode headerMode) {
        this.httpClient = createHttpClient();
        this.objectMapper = createObjectMapper();
        this.tokenManager = tokenManager;
        this.autoHeaders = (headerMode == HeaderMode.AUTO_HEADERS);
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
     * Make an authenticated GET request
     *
     * @param credential OAuth credential
     * @param url request URL
     * @return response body as string
     * @throws IOException if request fails
     */
    public String get(CCAPICredentialOauth credential, String url) throws IOException {
        return get(credential, url, null);
    }

    /**
     * Make an authenticated GET request with custom headers
     *
     * @param credential OAuth credential
     * @param url request URL
     * @param headers custom headers (can be null)
     * @return response body as string
     * @throws IOException if request fails
     */
    public String get(CCAPICredentialOauth credential, String url, java.util.Map<String, String> headers) throws IOException {
        var accessToken = tokenManager.getValidAccessToken(credential);

        var requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("User-Agent", DEFAULT_USER_AGENT)
                .addHeader("Accept", "*/*");

        // Add custom headers if provided
        if (headers != null) {
            headers.forEach(requestBuilder::addHeader);
        }

        var request = requestBuilder.get().build();
        return executeRequest(request);
    }

    /**
     * Make an authenticated POST request with JSON body
     *
     * @param credential OAuth credential
     * @param url request URL
     * @param jsonBody JSON request body
     * @return response body as string
     * @throws IOException if request fails
     */
    public String post(CCAPICredentialOauth credential, String url, String jsonBody) throws IOException {
        return post(credential, url, jsonBody, null);
    }

    /**
     * Make an authenticated POST request with JSON body and custom headers
     *
     * @param credential OAuth credential
     * @param url request URL
     * @param jsonBody JSON request body
     * @param headers custom headers (can be null)
     * @return response body as string
     * @throws IOException if request fails
     */
    public String post(CCAPICredentialOauth credential, String url, String jsonBody, java.util.Map<String, String> headers) throws IOException {
        var accessToken = tokenManager.getValidAccessToken(credential);

        var requestBody = RequestBody.create(jsonBody, JSON);

        var requestBuilder = new Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("User-Agent", DEFAULT_USER_AGENT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "*/*");

        // Add custom headers if provided
        if (headers != null) {
            headers.forEach(requestBuilder::addHeader);
        }

        var request = requestBuilder.build();
        return executeRequest(request);
    }

    /**
     * Make an authenticated POST request with object that will be serialized to JSON
     *
     * @param credential OAuth credential
     * @param url request URL
     * @param body object to serialize as JSON
     * @return response body as string
     * @throws IOException if request fails
     */
    public String post(CCAPICredentialOauth credential, String url, Object body) throws IOException {
        return post(credential, url, body, null);
    }

    /**
     * Make an authenticated POST request with object that will be serialized to JSON and custom headers
     *
     * @param credential OAuth credential
     * @param url request URL
     * @param body object to serialize as JSON
     * @param headers custom headers (can be null)
     * @return response body as string
     * @throws IOException if request fails
     */
    public String post(CCAPICredentialOauth credential, String url, Object body, java.util.Map<String, String> headers) throws IOException {
        var jsonBody = objectMapper.writeValueAsString(body);
        return post(credential, url, jsonBody, headers);
    }

    /**
     * Execute HTTP request and return response body
     */
    private String executeRequest(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                var errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Request failed: HTTP " + response.code() + " - " + errorBody);
            }

            var responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Empty response body");
            }

            return responseBody.string();
        }
    }

    /**
     * Parse JSON response to object
     *
     * @param json JSON string
     * @param clazz target class
     * @return parsed object
     * @throws IOException if parsing fails
     */
    public <T> T parseJson(String json, Class<T> clazz) throws IOException {
        return objectMapper.readValue(json, clazz);
    }

    /**
     * Get the token manager for direct access
     */
    public OAuthTokenManager getTokenManager() {
        return tokenManager;
    }

    /**
     * Get the object mapper for direct access
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Shutdown the HTTP client and release resources
     */
    public void shutdown() {
        httpClient.dispatcher().cancelAll();
        httpClient.connectionPool().evictAll();
        httpClient.dispatcher().executorService().shutdownNow();
        tokenManager.shutdown();
    }

    // ========================================================================
    // HIGH-LEVEL CONVENIENCE METHODS
    // ========================================================================
    // These methods hide the complexity of headers, models, and API quirks
    // from client code. For advanced use cases, use the low-level post/get methods.

    /**
     * Count tokens for a message (simplified API).
     * Uses default model and automatically adds correct headers.
     *
     * @param credential OAuth credential
     * @param messages List of messages (role + content)
     * @return Number of input tokens
     * @throws IOException if request fails
     */
    public int countTokens(CCAPICredentialOauth credential, java.util.List<java.util.Map<String, String>> messages) throws IOException {
        return countTokens(credential, OAuthApiConfig.getDefaultModel(), null, messages);
    }

    /**
     * Count tokens for a message with system prompt (simplified API).
     * Uses default model and automatically adds correct headers.
     *
     * @param credential OAuth credential
     * @param systemPrompt System prompt (can be null)
     * @param messages List of messages (role + content)
     * @return Number of input tokens
     * @throws IOException if request fails
     */
    public int countTokens(CCAPICredentialOauth credential, String systemPrompt, java.util.List<java.util.Map<String, String>> messages) throws IOException {
        return countTokens(credential, OAuthApiConfig.getDefaultModel(), systemPrompt, messages);
    }

    /**
     * Count tokens for a message with explicit model (simplified API).
     * Automatically adds correct headers if AUTO_HEADERS mode is enabled.
     *
     * @param credential OAuth credential
     * @param model Model identifier (use OAuthApiConfig.Models constants)
     * @param systemPrompt System prompt (can be null)
     * @param messages List of messages (role + content)
     * @return Number of input tokens
     * @throws IOException if request fails
     * @throws IllegalArgumentException if model is not supported for OAuth or if MANUAL_HEADERS mode requires headers
     */
    public int countTokens(CCAPICredentialOauth credential, String model, String systemPrompt, java.util.List<java.util.Map<String, String>> messages) throws IOException {
        // Validate model in AUTO_HEADERS mode
        if (autoHeaders && !OAuthApiConfig.isModelSupported(model)) {
            throw new IllegalArgumentException(
                "Model '" + model + "' is not supported for OAuth. " +
                "Supported models: " + String.join(", ", OAuthApiConfig.getSupportedModels())
            );
        }

        // Build request payload
        var payload = new java.util.HashMap<String, Object>();
        payload.put("model", model);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            payload.put("system", systemPrompt);
        }
        payload.put("messages", messages);

        // Get headers from config only in AUTO_HEADERS mode
        var headers = autoHeaders ? OAuthApiConfig.getTokenCountHeaders(model) : null;

        // Build URL
        var url = credential.resolvedCcapiApiUrl() + "/v1/messages/count_tokens";

        // Make request
        var response = post(credential, url, payload, headers);

        // Parse response
        var jsonNode = objectMapper.readTree(response);
        if (!jsonNode.has("input_tokens")) {
            throw new IOException("Response missing 'input_tokens' field: " + response);
        }

        return jsonNode.get("input_tokens").asInt();
    }

    /**
     * Call Messages API (simplified API).
     * Uses default model and parameters, automatically adds correct headers.
     *
     * @param credential OAuth credential
     * @param messages List of messages (role + content)
     * @return API response as JSON string
     * @throws IOException if request fails
     */
    public String callMessages(CCAPICredentialOauth credential, java.util.List<java.util.Map<String, String>> messages) throws IOException {
        return callMessages(credential, OAuthApiConfig.getDefaultModel(), null, 1024, messages);
    }

    /**
     * Call Messages API with system prompt (simplified API).
     * Uses default model and max_tokens, automatically adds correct headers.
     *
     * @param credential OAuth credential
     * @param systemPrompt System prompt (can be null)
     * @param messages List of messages (role + content)
     * @return API response as JSON string
     * @throws IOException if request fails
     */
    public String callMessages(CCAPICredentialOauth credential, String systemPrompt, java.util.List<java.util.Map<String, String>> messages) throws IOException {
        return callMessages(credential, OAuthApiConfig.getDefaultModel(), systemPrompt, 1024, messages);
    }

    /**
     * Call Messages API with explicit parameters (simplified API).
     * Automatically adds correct headers if AUTO_HEADERS mode is enabled.
     *
     * @param credential OAuth credential
     * @param model Model identifier (use OAuthApiConfig.Models constants)
     * @param systemPrompt System prompt (can be null)
     * @param maxTokens Maximum tokens to generate
     * @param messages List of messages (role + content)
     * @return API response as JSON string
     * @throws IOException if request fails
     * @throws IllegalArgumentException if model is not supported for OAuth in AUTO_HEADERS mode
     */
    public String callMessages(CCAPICredentialOauth credential, String model, String systemPrompt, int maxTokens, java.util.List<java.util.Map<String, String>> messages) throws IOException {
        // Validate model in AUTO_HEADERS mode
        if (autoHeaders && !OAuthApiConfig.isModelSupported(model)) {
            throw new IllegalArgumentException(
                "Model '" + model + "' is not supported for OAuth. " +
                "Supported models: " + String.join(", ", OAuthApiConfig.getSupportedModels())
            );
        }

        // Build request payload
        var payload = new java.util.HashMap<String, Object>();
        payload.put("model", model);
        payload.put("max_tokens", maxTokens);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            payload.put("system", systemPrompt);
        }
        payload.put("messages", messages);

        // Get headers from config only in AUTO_HEADERS mode
        var headers = autoHeaders ? OAuthApiConfig.getMessagesHeaders(model) : null;

        // Build URL
        var url = credential.resolvedCcapiApiUrl() + "/v1/messages";

        // Make request
        return post(credential, url, payload, headers);
    }

    /**
     * Get the current header mode configuration
     * @return true if AUTO_HEADERS mode, false if MANUAL_HEADERS mode
     */
    public boolean isAutoHeadersEnabled() {
        return autoHeaders;
    }
}
