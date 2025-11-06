package xyz.jphil.ccapis;

import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.jphil.ccapis.model.ConversationsData;
import xyz.jphil.ccapis.config.CCAPIConfig;
import xyz.jphil.ccapis.extract_from_curl.CurlCommandCredentialExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test class to understand the Conversations API response format
 */
public class TestConversationsAPI {
    
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
    
    public TestConversationsAPI() {
        this.objectMapper = new ObjectMapper();
        // Register Java 8 time module for OffsetDateTime support
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // Disable failing on unknown properties to be more flexible with API changes
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public void testListConversations() throws IOException {
        System.out.println("=== Testing List Conversations API ===");
        
        CCAPIConfig config = CurlCommandCredentialExtractor.extractCredentialsFromTestSettings();
        CCAPIConfig.Account account = config.getDefaultAccount();
        
        if (account == null) {
            System.out.println("Skipping test - No account available from configuration");
            return;
        }
        
        String url = getBaseUrl() + "/organizations/" + account.orgId() + "/chat_conversations";
        
        Request request = new Request.Builder()
                .url(url)
                .header("Cookie", "sessionKey=" + account.sessionKey())
                .header("User-Agent", account.userAgent())
                .header("Accept", "*/*")
                .header("Origin", getBaseURL())
                .header("Referer", getBaseURL() + "/")
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            System.out.println("Status Code: " + response.code());
            System.out.println("Response: " + responseBody);
            
            // Save response sample
            saveResponseSample("conversations_api_response.json", responseBody);
            
            // Parse the response to ConversationsData
            ConversationsData conversationsData = objectMapper.readValue(responseBody, ConversationsData.class);
            System.out.println("Parsed Conversations Data: " + conversationsData);
        }
    }
    
    private void saveResponseSample(String filename, String response) throws IOException {
        Path path = Paths.get("test-scripts", filename);
        Files.createDirectories(path.getParent());
        Files.write(path, response.getBytes());
        System.out.println("Response saved to: " + path.toAbsolutePath());
    }
    
    public static void main(String[] args) throws IOException {
        new TestConversationsAPI().testListConversations();
    }
}