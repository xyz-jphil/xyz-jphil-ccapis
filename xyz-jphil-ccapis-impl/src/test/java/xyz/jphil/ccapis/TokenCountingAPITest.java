package xyz.jphil.ccapis;

import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.jphil.ccapis.config.CCAPIConfig;
import xyz.jphil.ccapis.extract_from_curl.CurlCommandCredentialExtractor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Test to check if Claude has a token counting API equivalent to Anthropic's
 * Anthropic: POST /v1/messages/count_tokens
 */
public class TokenCountingAPITest {
    
    private static final String getApiBaseUrl() {
        String baseUrl = System.getenv("CCAPI_BASE_URL");
        if (baseUrl == null) {
            throw new RuntimeException("CCAPI_BASE_URL environment variable must be set");
        }
        // Ensure the URL starts with https://
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://" + baseUrl;
        }
        return baseUrl + "/api";
    }
    
    private static final String getBaseURL() {
        String baseUrl = System.getenv("CCAPI_BASE_URL");
        if (baseUrl == null) {
            throw new RuntimeException("CCAPI_BASE_URL environment variable must be set");
        }
        // Ensure the URL starts with https://
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://" + baseUrl;
        }
        return baseUrl;
    }
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    
    public TokenCountingAPITest() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public void testTokenCountingAPI() throws IOException {
        System.out.println("=== CLAUDE TOKEN COUNTING API TEST ===");
        System.out.println("Checking for equivalent to Anthropic's POST /v1/messages/count_tokens");
        System.out.println();
        
        CCAPIConfig config = CurlCommandCredentialExtractor.extractCredentialsFromTestSettings();
        CCAPIConfig.Account account = config.getDefaultAccount();
        
        if (account == null) {
            System.out.println("ERROR: No account available from configuration");
            return;
        }
        
        System.out.println("Using account:");
        System.out.println("  Org ID: " + account.orgId());
        System.out.println("  Session Key starts with: " + account.sessionKey().substring(0, 15) + "...");
        System.out.println();
        
        // Test various potential token counting endpoints
        testPotentialEndpoints(account);
    }
    
    private void testPotentialEndpoints(CCAPIConfig.Account account) throws IOException {
        // Common patterns for token counting endpoints
        String[] potentialEndpoints = {
            "/organizations/" + account.orgId() + "/token_count",           // Direct translation
            "/organizations/" + account.orgId() + "/messages/token_count",  // Similar to Anthropic structure
            "/organizations/" + account.orgId() + "/count_tokens",         // Alternative naming
            "/token_count",                                                 // Global endpoint
            "/count_tokens",                                                // Simple naming
            "/organizations/" + account.orgId() + "/tokenizer/count",      // Nested approach
            "/tokenizer/count",                                            // Global tokenizer
            "/models/token_count",                                         // Model-specific
            "/tokenizer"                                                    // Just tokenizer endpoint
        };
        
        String testMessage = "Hello this is a test message to check if you have a token counting API. How many tokens does this message contain?";
        
        for (String endpointPath : potentialEndpoints) {
            String url = getApiBaseUrl() + endpointPath;
            System.out.println("Testing endpoint: " + endpointPath);
            
            // Test with GET request
            testGETRequest(account, url, endpointPath);
            
            // Test with POST request (more likely for token counting)
            testPOSTRequest(account, url, endpointPath, testMessage);
            
            System.out.println();
        }
        
        // Also check if there's token info in existing API responses
        checkExistingAPIsForTokenInfo(account);
    }
    
    private void testGETRequest(CCAPIConfig.Account account, String url, String endpointPath) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Cookie", "sessionKey=" + account.sessionKey())
                .header("User-Agent", account.userAgent())
                .header("Accept", "*/*")
                .header("Origin", getBaseURL())
                .header("Referer", getBaseURL() + "/")
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (response.isSuccessful()) {
                System.out.println("  ✅ GET " + endpointPath + " - SUCCESS (Status: " + response.code() + ")");
                System.out.println("     Response: " + responseBody.substring(0, Math.min(200, responseBody.length())) + "...");
                tryToParseTokenResponse(responseBody);
            } else if (response.code() != 404 && response.code() != 405) {
                // Not a "not found" or "method not allowed" error
                System.out.println("  ⚠️  GET " + endpointPath + " - UNEXPECTED (Status: " + response.code() + ")");
                System.out.println("     Response: " + responseBody.substring(0, Math.min(100, responseBody.length())) + "...");
            }
        }
    }
    
    private void testPOSTRequest(CCAPIConfig.Account account, String url, String endpointPath, String message) throws IOException {
        // Try different POST payloads that might work for token counting
        String[] payloads = {
            String.format("{\"message\":\"%s\"}", message.replace("\"", "\\\"")),
            String.format("{\"prompt\":\"%s\"}", message.replace("\"", "\\\"")),
            String.format("{\"text\":\"%s\"}", message.replace("\"", "\\\"")),
            String.format("{\"content\":\"%s\"}", message.replace("\"", "\\\"")),
            String.format("{\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}", message.replace("\"", "\\\"")),
            "{\"model\":\"claude-3-opus-20240229\",\"messages\":[{\"role\":\"user\",\"content\":\"test\"}]}",
            "{\"model\":\"claude-3-sonnet-20240229\",\"prompt\":\"test\"}"
        };
        
        for (String json : payloads) {
            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .header("Cookie", "sessionKey=" + account.sessionKey())
                    .header("User-Agent", account.userAgent())
                    .header("Accept", "*/*")
                    .header("Content-Type", "application/json")
                    .header("Origin", getBaseURL())
                    .header("Referer", getBaseURL() + "/")
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();
                if (response.isSuccessful()) {
                    System.out.println("  ✅ POST " + endpointPath + " - SUCCESS (Status: " + response.code() + ")");
                    System.out.println("     Payload: " + json);
                    System.out.println("     Response: " + responseBody.substring(0, Math.min(200, responseBody.length())) + "...");
                    tryToParseTokenResponse(responseBody);
                    return; // Found a working endpoint, no need to try other payloads
                } else if (response.code() != 404 && response.code() != 405 && response.code() != 400) {
                    // Not a "not found", "method not allowed", or "bad request" error
                    System.out.println("  ⚠️  POST " + endpointPath + " - UNEXPECTED (Status: " + response.code() + ")");
                    System.out.println("     Payload: " + json);
                    System.out.println("     Response: " + responseBody.substring(0, Math.min(100, responseBody.length())) + "...");
                }
            } catch (Exception e) {
                // Continue trying other payloads
            }
        }
    }
    
    private void tryToParseTokenResponse(String responseBody) {
        try {
            // Try to parse as JSON and look for token-related fields
            Object parsed = objectMapper.readValue(responseBody, Object.class);
            System.out.println("     Parsed response type: " + parsed.getClass().getSimpleName());
            
            // Look for common token field names
            if (responseBody.toLowerCase().contains("token") || 
                responseBody.toLowerCase().contains("count") ||
                responseBody.contains("input_tokens") ||
                responseBody.contains("output_tokens")) {
                System.out.println("     ⭐ Contains token-related fields!");
            }
        } catch (Exception e) {
            // Not valid JSON, that's okay
            System.out.println("     Response is not valid JSON");
        }
    }
    
    private void checkExistingAPIsForTokenInfo(CCAPIConfig.Account account) throws IOException {
        System.out.println("Checking existing APIs for token information...");
        
        // 1. Check conversation creation response for token info
        System.out.println("1. Checking conversation creation response...");
        String conversationUrl = getBaseURL() + "/organizations/" + account.orgId() + "/chat_conversations";
        String uuid = java.util.UUID.randomUUID().toString();
        String json = String.format(
            "{\"uuid\":\"%s\",\"name\":\"Token Test Conversation\",\"is_temporary\":true,\"include_conversation_preferences\":true}",
            uuid
        );
        
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        
        Request request = new Request.Builder()
                .url(conversationUrl)
                .post(body)
                .header("Cookie", "sessionKey=" + account.sessionKey())
                .header("User-Agent", account.userAgent())
                .header("Accept", "*/*")
                .header("Content-Type", "application/json")
                .header("Origin", getBaseURL())
                .header("Referer", getBaseURL() + "/")
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                System.out.println("   Conversation creation response analyzed for token info:");
                
                // Look for token-related information in the response
                if (responseBody.contains("token") || 
                    responseBody.contains("count") ||
                    responseBody.contains("usage") ||
                    responseBody.contains("input") ||
                    responseBody.contains("output")) {
                    System.out.println("   ⭐ Found potential token-related fields in conversation response!");
                    System.out.println("   Response preview: " + responseBody.substring(0, Math.min(200, responseBody.length())) + "...");
                } else {
                    System.out.println("   No obvious token information found in conversation response");
                }
            }
        }
        
        // 2. Check usage API for token info
        System.out.println("2. Checking usage API...");
        String usageUrl = getApiBaseUrl() + "/organizations/" + account.orgId() + "/usage";
        
        Request usageRequest = new Request.Builder()
                .url(usageUrl)
                .header("Cookie", "sessionKey=" + account.sessionKey())
                .header("User-Agent", account.userAgent())
                .header("Accept", "*/*")
                .header("Origin", getBaseURL())
                .header("Referer", getBaseURL() + "/")
                .build();
        
        try (Response response = client.newCall(usageRequest).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                System.out.println("   Usage API response analyzed:");
                System.out.println("   Response: " + responseBody);
                
                if (responseBody.contains("token")) {
                    System.out.println("   ⭐ Found token-related information in usage API!");
                }
            }
        }
    }
    
    public static void main(String[] args) throws IOException {
        new TokenCountingAPITest().testTokenCountingAPI();
    }
}