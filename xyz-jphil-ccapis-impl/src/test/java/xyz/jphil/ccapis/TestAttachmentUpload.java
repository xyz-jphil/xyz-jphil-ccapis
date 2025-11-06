package xyz.jphil.ccapis;

import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.jphil.ccapis.config.CCAPIConfig;
import xyz.jphil.ccapis.extract_from_curl.CurlCommandCredentialExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test class to understand the attachment upload API
 */
public class TestAttachmentUpload {
    
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
    
    public TestAttachmentUpload() {
        this.objectMapper = new ObjectMapper();
        // Register Java 8 time module for OffsetDateTime support
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // Disable failing on unknown properties to be more flexible with API changes
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public void testAttachmentUpload() throws IOException {
        System.out.println("Testing Attachment Upload...");
        
        CCAPIConfig config = CurlCommandCredentialExtractor.extractCredentialsFromTestSettings();
        CCAPIConfig.Account account = config.getDefaultAccount();
        
        if (account == null) {
            System.out.println("Skipping test - No account available from configuration");
            return;
        }
        
        // Create a temporary text file for testing
        Path tempFile = Files.createTempFile("test_attachment", ".txt");
        Files.write(tempFile, "This is a test attachment file for CCAPI.".getBytes());
        
        try {
            String url = getBaseUrl() + "/convert_document";
            
            // Create multipart request body
            RequestBody fileBody = RequestBody.create(
                Files.readAllBytes(tempFile), 
                MediaType.parse("text/plain")
            );
            
            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", tempFile.getFileName().toString(), fileBody)
                    .addFormDataPart("orgUuid", account.orgId())
                    .build();
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Cookie", "sessionKey=" + account.sessionKey())
                    .header("User-Agent", account.userAgent())
                    .header("Accept", "*/*")
                    .header("Origin", getBaseURL())
                    .header("Referer", getBaseURL() + "/")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();
                System.out.println("Status Code: " + response.code());
                System.out.println("Raw Response: " + responseBody);
                
                // Save response sample
                saveResponseSample("attachment_upload_response.json", responseBody);
            }
        } finally {
            // Clean up the temporary file
            Files.deleteIfExists(tempFile);
        }
    }
    
    private void saveResponseSample(String filename, String response) {
        try {
            Path path = Paths.get("test-scripts", filename);
            Files.createDirectories(path.getParent());
            Files.write(path, response.getBytes());
            System.out.println("Response saved to: " + path.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error saving response sample: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) throws IOException {
        new TestAttachmentUpload().testAttachmentUpload();
    }
}