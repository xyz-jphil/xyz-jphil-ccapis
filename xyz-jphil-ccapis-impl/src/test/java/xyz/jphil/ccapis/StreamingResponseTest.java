package xyz.jphil.ccapis;

import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.jphil.ccapis.config.CCAPIConfig;
import xyz.jphil.ccapis.extract_from_curl.CurlCommandCredentialExtractor;
import xyz.jphil.ccapis.model.ChatCreationResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Test to understand Claude's streaming response format
 */
public class StreamingResponseTest {
    
    private static final String getBaseUrl() {
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
    
    public StreamingResponseTest() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public void testStreamingResponse() throws IOException {
        System.out.println("=== CLAUDE STREAMING RESPONSE TEST ===");
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
        
        // Step 1: Create a new conversation
        System.out.println("1. CREATING A NEW CONVERSATION...");
        ChatCreationResponse conversation = createNewConversation(account);
        if (conversation == null) {
            System.out.println("ERROR: Failed to create conversation");
            return;
        }
        
        System.out.println("   ✓ Conversation created successfully!");
        System.out.println("   - UUID: " + conversation.uuid());
        System.out.println();
        
        // Step 2: Send a query with streaming headers
        System.out.println("2. SENDING QUERY WITH STREAMING HEADERS...");
        sendStreamingQuery(account, conversation.uuid(), "Hello Claude! Please tell me a short story in 3 sentences.");
    }
    
    private ChatCreationResponse createNewConversation(CCAPIConfig.Account account) throws IOException {
        String url = getBaseUrl() + "/organizations/" + account.orgId() + "/chat_conversations";
        
        String uuid = java.util.UUID.randomUUID().toString();
        String json = String.format(
            "{\"uuid\":\"%s\",\"name\":\"Streaming Test Conversation\",\"is_temporary\":false,\"include_conversation_preferences\":true}",
            uuid
        );
        
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
                .header("Accept-Encoding", "gzip, deflate, br")
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return objectMapper.readValue(response.body().string(), ChatCreationResponse.class);
            } else {
                System.out.println("   - ERROR: " + response.code() + " - " + response.body().string());
                return null;
            }
        }
    }
    
    private void sendStreamingQuery(CCAPIConfig.Account account, String conversationId, String query) throws IOException {
        String url = getBaseUrl() + "/organizations/" + account.orgId() + "/chat_conversations/" + conversationId + "/completion";
        
        String json = String.format(
            "{\"prompt\":\"%s\",\"timezone\":\"UTC\"}",
            query.replace("\"", "\\\"")
        );
        
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Cookie", "sessionKey=" + account.sessionKey())
                .header("User-Agent", account.userAgent())
                .header("Accept", "text/event-stream") // Request streaming response
                .header("Content-Type", "application/json")
                .header("Origin", getBaseURL())
                .header("Referer", getBaseURL() + "/")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Cache-Control", "no-cache")
                .build();
        
        System.out.println("   - Sending request with streaming headers...");
        System.out.println("   - Accept: text/event-stream");
        System.out.println();
        
        try (Response response = client.newCall(request).execute()) {
            System.out.println("   - Response Status: " + response.code());
            System.out.println("   - Content-Type: " + response.header("Content-Type"));
            System.out.println("   - Transfer-Encoding: " + response.header("Transfer-Encoding"));
            
            if (response.isSuccessful()) {
                System.out.println("   - Reading streaming response...");
                System.out.println("   - Response Headers:");
                response.headers().forEach(header -> {
                    System.out.println("     " + header.getFirst() + ": " + header.getSecond());
                });
                
                // Read and display the response body
                String responseBody = response.body().string();
                System.out.println();
                System.out.println("   - Response Body Preview (" + Math.min(500, responseBody.length()) + " chars):");
                System.out.println("     " + responseBody.substring(0, Math.min(500, responseBody.length())).replace("\n", "\n     "));
                
                if (responseBody.length() > 500) {
                    System.out.println("     ... (" + (responseBody.length() - 500) + " more characters)");
                }
                
                // Check if it looks like SSE format
                if (responseBody.contains("data:")) {
                    System.out.println();
                    System.out.println("   ✓ This appears to be Server-Sent Events (SSE) format!");
                    System.out.println("   - Streaming is supported and working correctly");
                } else if (responseBody.startsWith("\u001f\u008b")) { // GZIP magic bytes
                    System.out.println();
                    System.out.println("   ⚠ This appears to be GZIP compressed data");
                    System.out.println("   - Response is compressed, need to decompress to read content");
                } else {
                    System.out.println();
                    System.out.println("   ? Response format unclear");
                }
            } else {
                System.out.println("   - ERROR: " + response.code() + " - " + response.body().string());
            }
        }
    }
    
    public static void main(String[] args) throws IOException {
        new StreamingResponseTest().testStreamingResponse();
    }
}