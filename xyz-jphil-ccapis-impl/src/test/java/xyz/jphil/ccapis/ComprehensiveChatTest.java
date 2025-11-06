package xyz.jphil.ccapis;

import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.jphil.ccapis.config.CCAPIConfig;
import xyz.jphil.ccapis.extract_from_curl.CurlCommandCredentialExtractor;
import xyz.jphil.ccapis.model.ChatCreationResponse;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive test to verify the full CCAPI workflow:
 * 1. Create a new conversation
 * 2. Send a query to it
 * 3. Get and analyze the response
 */
public class ComprehensiveChatTest {
    
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
    
    public ComprehensiveChatTest() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        // Register Java 8 time module for OffsetDateTime support
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // Disable failing on unknown properties to be more flexible with API changes
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public void runFullWorkflowTest() throws IOException {
        System.out.println("=== COMPREHENSIVE CCAPI WORKFLOW TEST ===");
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
        System.out.println("  User Agent: " + account.userAgent());
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
        System.out.println("   - Name: " + conversation.name());
        System.out.println("   - Created at: " + conversation.createdAt());
        System.out.println();
        
        // Step 2: Send a query to the conversation
        System.out.println("2. SENDING A QUERY TO THE CONVERSATION...");
        String query = "Hello, this is a test. Please tell me your name and what you can help me with. Keep your response brief.";
        String response = sendQueryToConversation(account, conversation.uuid(), query);
        if (response == null) {
            System.out.println("ERROR: Failed to send query or get response");
            return;
        }
        
        System.out.println("   ✓ Query sent and response received!");
        System.out.println("   - Query: " + query);
        System.out.println("   - Raw Response Length: " + response.length() + " characters");
        System.out.println("   - Raw Response Preview: " + response.substring(0, Math.min(200, response.length())) + "...");
        System.out.println();
        
        // Step 3: Analyze the response
        System.out.println("3. ANALYZING THE RESPONSE...");
        analyzeResponse(response);
        System.out.println();
        
        System.out.println("=== WORKFLOW TEST COMPLETED SUCCESSFULLY ===");
        System.out.println("Full workflow: Create Conversation → Send Query → Receive Response");
    }
    
    private ChatCreationResponse createNewConversation(CCAPIConfig.Account account) throws IOException {
        String url = getBaseUrl() + "/organizations/" + account.orgId() + "/chat_conversations";
        
        // Generate UUID for conversation
        String uuid = java.util.UUID.randomUUID().toString();
        
        String json = String.format(
            "{\"uuid\":\"%s\",\"name\":\"API Test Conversation - %s\",\"is_temporary\":false,\"include_conversation_preferences\":true}",
            uuid, java.time.Instant.now().toString().replace(":", "-").replace(".", "-")
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
            String responseBody = response.body().string();
            System.out.println("   - API Response Status: " + response.code());
            System.out.println("   - API Response Body: " + responseBody);
            
            if (response.isSuccessful()) {
                // Parse the response to ChatCreationResponse
                ChatCreationResponse chatResponse = objectMapper.readValue(responseBody, ChatCreationResponse.class);
                return chatResponse;
            } else {
                System.out.println("   - ERROR: Failed to create chat. Response: " + responseBody);
                return null;
            }
        }
    }
    
    private String sendQueryToConversation(CCAPIConfig.Account account, String conversationId, String query) throws IOException {
        String url = getBaseUrl() + "/organizations/" + account.orgId() + "/chat_conversations/" + conversationId + "/completion";
        
        String json = String.format(
            "{\"prompt\":\"%s\",\"timezone\":\"UTC\"}",
            query.replace("\"", "\\\"") // Escape quotes in the message
        );
        
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Cookie", "sessionKey=" + account.sessionKey())
                .header("User-Agent", account.userAgent())
                .header("Accept", "text/plain, */*; q=0.01") // Changed from text/event-stream to text/plain for testing
                .header("Content-Type", "application/json")
                .header("Origin", getBaseURL())
                .header("Referer", getBaseURL() + "/")
                .header("Accept-Encoding", "gzip, deflate, br")
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                return responseBody;
            } else {
                System.out.println("   - ERROR: Failed to send query. Status: " + response.code());
                String errorBody = response.body().string();
                System.out.println("   - ERROR Body: " + errorBody);
                return null;
            }
        }
    }
    
    private void analyzeResponse(String response) {
        System.out.println("   - Response Type: " + (response.startsWith("data:") ? "Server-Sent Events (Streaming)" : "Direct JSON"));
        
        if (response.startsWith("data:")) {
            System.out.println("   - This appears to be a streaming response (Server-Sent Events)");
            System.out.println("   - Each line likely contains: data: {json_content}");
            
            // Count the number of data lines
            String[] lines = response.split("\n");
            long dataLineCount = java.util.Arrays.stream(lines)
                    .filter(line -> line.trim().startsWith("data:"))
                    .count();
            System.out.println("   - Total data lines in stream: " + dataLineCount);
            
            // Show a few sample data lines
            System.out.println("   - Sample data lines:");
            java.util.Arrays.stream(lines)
                    .filter(line -> line.trim().startsWith("data:"))
                    .limit(3)
                    .forEach(line -> System.out.println("     " + line.substring(0, Math.min(line.length(), 100)) + (line.length() > 100 ? "..." : "")));
        } else {
            System.out.println("   - This appears to be a direct JSON response");
            try {
                // Try to parse as JSON to see if it's a complete response
                Object parsed = objectMapper.readValue(response, Object.class);
                System.out.println("   - Successfully parsed as JSON");
                System.out.println("   - Response structure appears to be complete");
            } catch (Exception e) {
                System.out.println("   - Could not parse as JSON, may be plain text or malformed");
            }
        }
        
        // Look for common response indicators
        if (response.toLowerCase().contains("claude")) {
            System.out.println("   - Contains 'Claude' reference: Yes");
        }
        if (response.toLowerCase().contains("sorry") || response.toLowerCase().contains("cannot")) {
            System.out.println("   - Contains limitation responses: Yes");
        }
    }
    
    public static void main(String[] args) throws IOException {
        new ComprehensiveChatTest().runFullWorkflowTest();
    }
}