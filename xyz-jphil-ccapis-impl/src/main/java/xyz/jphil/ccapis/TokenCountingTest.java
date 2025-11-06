package xyz.jphil.ccapis;

import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.model.CCAPICredential;

import java.io.IOException;

/**
 * Test to discover token counting API endpoints
 * Now uses shared CCAPIsCredentials.xml
 */
public class TokenCountingTest {

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final CCAPICredential credential;

    public TokenCountingTest() throws Exception {
        var credentialsManager = new CredentialsManager();
        var credentials = credentialsManager.load();
        this.credential = credentials.credentials().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No credentials found"));

        this.client = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private String ensureHttpsPrefix(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }

    public void runTests() {
        System.out.println("==============================================");
        System.out.println("   CCAPI Token Counting API Discovery Test");
        System.out.println("==============================================");
        System.out.println();
        System.out.println("Using credential: " + credential.id() + " (" + credential.name() + ")");
        System.out.println("Base URL: " + credential.resolvedCcapiBaseUrl());
        System.out.println();

        System.out.println("Using account:");
        System.out.println("  ID: " + credential.id());
        System.out.println("  Org ID: " + credential.orgId());
        System.out.println("  Session Key: " + credential.key().substring(0, 20) + "...");
        System.out.println();

        testPotentialEndpoints(credential);
        testCompletionResponseForTokens(credential);
    }

    private void testPotentialEndpoints(CCAPICredential credential) {
        System.out.println("=== Testing Potential Token Counting Endpoints ===");
        System.out.println();

        String testMessage = "Hello! This is a test message to check token counting. How many tokens does this contain?";

        // Test various endpoint patterns
        String[] endpoints = {
            "/api/tokenize",
            "/api/token_count",
            "/api/count_tokens",
            "/api/organizations/" + credential.orgId() + "/tokenize",
            "/api/organizations/" + credential.orgId() + "/token_count",
            "/api/organizations/" + credential.orgId() + "/count_tokens",
            "/api/tokenizer/count",
            "/api/models/token_count"
        };

        for (String endpoint : endpoints) {
            System.out.println("Testing: " + endpoint);
            testEndpoint(credential, endpoint, testMessage);
            System.out.println();
        }
    }

    private void testEndpoint(CCAPICredential credential, String endpointPath, String message) {
        var baseUrl = credential.resolvedCcapiBaseUrl();
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://" + baseUrl;
        }
        var url = baseUrl + endpointPath;

        // Try different payload formats
        String[] payloads = {
            String.format("{\"text\":\"%s\"}", escapeJson(message)),
            String.format("{\"prompt\":\"%s\"}", escapeJson(message)),
            String.format("{\"message\":\"%s\"}", escapeJson(message)),
            String.format("{\"content\":\"%s\"}", escapeJson(message)),
            String.format("{\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}", escapeJson(message))
        };

        for (int i = 0; i < payloads.length; i++) {
            var json = payloads[i];
            var requestBody = RequestBody.create(json, MediaType.get("application/json"));

            var request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Cookie", "sessionKey=" + credential.key())
                    .addHeader("User-Agent", getUserAgent(credential))
                    .addHeader("Accept", "*/*")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Origin", baseUrl)
                    .addHeader("Referer", baseUrl + "/")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    var responseBody = response.body().string();
                    System.out.println("  ✅ SUCCESS (HTTP " + response.code() + ")");
                    System.out.println("  Payload format: " + getPayloadFormat(i));
                    System.out.println("  Response: " + responseBody);
                    analyzeTokenResponse(responseBody);
                    return; // Success, no need to try other payloads
                }
            } catch (IOException e) {
                // Continue to next payload
            }
        }

        System.out.println("  ❌ Not found or not accessible");
    }

    private void testCompletionResponseForTokens(CCAPICredential credential) {
        System.out.println("=== Analyzing Completion Response for Token Info ===");
        System.out.println();

        try {
            // Create a temporary conversation
            var client = new CCAPIClient();
            var chat = client.createChat(credential, "Token Analysis Test", true);
            System.out.println("Created test conversation: " + chat.uuid());

            // Send a message
            var response = client.sendMessage(credential, chat.uuid(), "Count tokens in this short message");

            // Analyze for token information
            System.out.println();
            System.out.println("Analyzing response for token-related fields...");
            analyzeTokenResponse(response);

            client.shutdown();

        } catch (Exception e) {
            System.err.println("Error testing completion response: " + e.getMessage());
        }
    }

    private void analyzeTokenResponse(String response) {
        var lower = response.toLowerCase();

        if (lower.contains("token") || lower.contains("count") ||
            lower.contains("input_tokens") || lower.contains("output_tokens") ||
            lower.contains("usage")) {
            System.out.println("  ⭐ Response contains token-related information!");

            // Try to extract token counts
            if (response.contains("input_tokens")) {
                System.out.println("  → Found 'input_tokens' field");
            }
            if (response.contains("output_tokens")) {
                System.out.println("  → Found 'output_tokens' field");
            }
            if (response.contains("total_tokens") || response.contains("token_count")) {
                System.out.println("  → Found total token count field");
            }
        } else {
            System.out.println("  ℹ️  No obvious token information in response");
        }
    }

    private String getUserAgent(CCAPICredential credential) {
        return (credential.ua() != null && !credential.ua().isEmpty())
                ? credential.ua()
                : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36";
    }

    private String escapeJson(String text) {
        return text.replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String getPayloadFormat(int index) {
        String[] formats = {"text", "prompt", "message", "content", "messages array"};
        return formats[index];
    }

    public static void main(String[] args) {
        try {
            var test = new TokenCountingTest();
            test.runTests();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
