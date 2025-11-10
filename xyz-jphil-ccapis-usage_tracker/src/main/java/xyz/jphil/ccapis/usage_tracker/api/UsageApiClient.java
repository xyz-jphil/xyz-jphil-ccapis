package xyz.jphil.ccapis.usage_tracker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import xyz.jphil.ccapis.model.CCAPICredential;
import xyz.jphil.ccapis.model.UsageData;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * API client to fetch usage data from CCAPI endpoints
 * Uses OkHttp for HTTP requests
 */
public class UsageApiClient {

    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Create usage API client (stateless - credentials passed to each method)
     */
    public UsageApiClient() {
        this.objectMapper = new ObjectMapper();
        // Register Java 8 time module for OffsetDateTime support
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // Disable failing on unknown properties to be more flexible with API changes
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    /**
     * Fetch usage data for a specific organization using CCAPICredential from base module.
     * Each credential now has its own baseUrl.
     * curl command syntax:
     * curl_chrome100.bat --insecure "https://%CCAPI_BASE_URL%/api/organizations/<orgid_value>/usage" -b "sessionKey=<key_value>;"
     */
    public UsageData fetchUsage(CCAPICredential credential) throws IOException {
        // Use credential's own baseUrl (overrides the one passed to constructor)
        var credentialBaseUrl = credential.resolvedCcapiBaseUrl();

        // Validate baseUrl is not null
        if (credentialBaseUrl == null || credentialBaseUrl.trim().isEmpty()) {
            throw new IOException("ccapiBaseUrl is not set for credential: " + credential.id());
        }

        if (!credentialBaseUrl.startsWith("http://") && !credentialBaseUrl.startsWith("https://")) {
            credentialBaseUrl = "https://" + credentialBaseUrl;
        }

        var url = String.format("%s/api/organizations/%s/usage", credentialBaseUrl, credential.orgId());

        // Use credential's ua if specified, otherwise fall back to default
        var userAgent = (credential.ua() != null && !credential.ua().isEmpty())
                ? credential.ua()
                : DEFAULT_USER_AGENT;

        // Log which user-agent is being used (only in verbose mode)
        if (isVerboseMode()) {
            var uaSource = (credential.ua() != null && !credential.ua().isEmpty()) ? "custom" : "default";
            System.out.println("[DEBUG] Using " + uaSource + " User-Agent for " + credential.id() + ": " + userAgent);
        }

        var request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", "sessionKey=" + credential.key())
                .addHeader("User-Agent", userAgent)
                .addHeader("Accept", "*/*")
                .addHeader("Origin", credentialBaseUrl)
                .addHeader("Referer", credentialBaseUrl + "/")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code() + " for org: " + credential.orgId());
            }

            var responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Empty response body for org: " + credential.orgId());
            }

            return objectMapper.readValue(responseBody.string(), UsageData.class);
        }
    }

    /**
     * Fetch usage with retry on failure
     */
    public UsageData fetchUsageWithRetry(CCAPICredential credential, int maxRetries) {
        IOException lastException = null;

        for (int i = 0; i < maxRetries; i++) {
            try {
                return fetchUsage(credential);
            } catch (IOException e) {
                lastException = e;
                if (isVerboseMode()) {
                    System.err.println("[DEBUG] Retry " + (i + 1) + "/" + maxRetries + " for " + credential.id() + ": " + e.getMessage());
                }
                // Wait before retry (exponential backoff)
                try {
                    Thread.sleep((long) Math.pow(2, i) * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // Build detailed error message
        var errorMsg = String.format("Failed to fetch usage after %d retries for credential '%s'",
            maxRetries, credential.id());

        if (lastException != null) {
            var detailedMsg = lastException.getMessage();
            if (detailedMsg != null && detailedMsg.contains("ccapiBaseUrl")) {
                errorMsg += " - ccapiBaseUrl is missing or invalid in credentials.xml";
            } else {
                errorMsg += " - " + detailedMsg;
            }
        }

        throw new RuntimeException(errorMsg, lastException);
    }

    /**
     * Shutdown the HTTP client and release resources
     * Call this when done to ensure immediate program exit
     */
    public void shutdown() {
        // Cancel all calls first
        httpClient.dispatcher().cancelAll();

        // Evict all connections
        httpClient.connectionPool().evictAll();

        // Force shutdown executor (don't wait)
        httpClient.dispatcher().executorService().shutdownNow();
    }

    /**
     * Check if verbose/debug mode is enabled
     * Controlled by system property: xyz.jphil.ccapis.verbose
     */
    private boolean isVerboseMode() {
        return "true".equalsIgnoreCase(System.getProperty("xyz.jphil.ccapis.verbose"));
    }
}
