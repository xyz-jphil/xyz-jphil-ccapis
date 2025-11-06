package xyz.jphil.ccapis;

import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;
import xyz.jphil.ccapis.model.UsageData;
import xyz.jphil.ccapis.model.ConversationsData;
import xyz.jphil.ccapis.model.SingleConversation;
import xyz.jphil.ccapis.model.AttachmentData;
import xyz.jphil.ccapis.extract_from_curl.CurlCommandCredentialExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import xyz.jphil.ccapis.config.CCAPIConfig;

/**
 * Test class for CCAPI experiments
 * This class will run experiments to understand the API behavior
 */
public class CCAPIExperimentTest {
    
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
    
    public CCAPIExperimentTest() {
        this.objectMapper = new ObjectMapper();
        // Register Java 8 time module for OffsetDateTime support
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // Disable failing on unknown properties to be more flexible with API changes
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    // Use the configuration system instead of environment variables
    private final xyz.jphil.ccapis.config.CCAPIConfig config = 
        xyz.jphil.ccapis.extract_from_curl.CurlCommandCredentialExtractor.extractCredentialsFromTestSettings();
    
    @Test
    public void testUsageAPI() throws IOException {
        System.out.println("=== Testing Usage API ===");
        
        CCAPIConfig.Account account = config.getDefaultAccount();
        if (account == null) {
            System.out.println("Skipping test - No account available from configuration");
            return;
        }
        
        String url = getBaseUrl() + "/organizations/" + account.orgId() + "/usage";
        
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
            // redo this ->
            // saveResponseSample("usage_api_response.json", responseBody);
            
            // Parse the response to UsageData
            UsageData usageData = objectMapper.readValue(responseBody, UsageData.class);
            System.out.println("Parsed Usage Data: " + usageData);
            
            // Verify response structure
            assertNotNull(usageData.fiveHour());
            //assertNotNull(usageData.sevenDay());
            //assertNotNull(usageData.sevenDayOauthApps());
            //assertNotNull(usageData.sevenDayOpus());
        }
    }
    
}