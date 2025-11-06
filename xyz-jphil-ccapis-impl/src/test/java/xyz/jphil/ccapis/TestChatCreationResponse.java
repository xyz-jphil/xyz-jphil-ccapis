package xyz.jphil.ccapis;

import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.jphil.ccapis.config.CCAPIConfig;
import xyz.jphil.ccapis.extract_from_curl.CurlCommandCredentialExtractor;

import java.io.IOException;

/**
 * Test class to see the actual chat creation response format
 */
public class TestChatCreationResponse {
    
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
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper;
    
    public TestChatCreationResponse() {
        this.objectMapper = new ObjectMapper();
        // Register Java 8 time module for OffsetDateTime support
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // Disable failing on unknown properties to be more flexible with API changes
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public void testChatCreationRaw() throws IOException {
        System.out.println("Testing Chat Creation - Raw Response...");
        
        CCAPIConfig config = CurlCommandCredentialExtractor.extractCredentialsFromTestSettings();
        CCAPIConfig.Account account = config.getDefaultAccount();
        
        if (account == null) {
            System.out.println("Skipping test - No account available from configuration");
            return;
        }
        
        String url = getBaseUrl() + "/organizations/" + account.orgId() + "/chat_conversations";
        
        // Generate UUID for conversation
        String uuid = java.util.UUID.randomUUID().toString();
        
        String json = String.format(
            "{\"uuid\":\"%s\",\"name\":\"API Test Conversation\",\"is_temporary\":false,\"include_conversation_preferences\":true}",
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
            String responseBody = response.body().string();
            System.out.println("Status Code: " + response.code());
            System.out.println("Raw Response: " + responseBody);
            
            // Save response sample
            saveResponseSample("chat_creation_response.json", responseBody);
        }
    }
    
    private void saveResponseSample(String filename, String response) {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("test-scripts", filename);
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, response.getBytes());
            System.out.println("Response saved to: " + path.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error saving response sample: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) throws IOException {
        new TestChatCreationResponse().testChatCreationRaw();
    }
}