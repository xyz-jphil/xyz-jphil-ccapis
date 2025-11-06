package xyz.jphil.ccapis.tokencounter.providers.freshfriedfish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import xyz.jphil.ccapis.tokencounter.api.ProviderInfo;
import xyz.jphil.ccapis.tokencounter.api.TokenizationResult;
import xyz.jphil.ccapis.tokencounter.api.TokenizerException;
import xyz.jphil.ccapis.tokencounter.api.TokenizerService;

/**
 * TokenizerService implementation using FreshFriedFish's Vercel tokenizer API.
 * Uses https://claude-tokenizer.vercel.app/api for accurate Claude token counting.
 */
public class FreshFriedFishTokenizerService implements TokenizerService {

    private static final String API_URL = "https://claude-tokenizer.vercel.app/api";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ProviderInfo PROVIDER_INFO = ProviderInfo.anthropicClaudeTokenizer();

    private final HttpClient httpClient;
    private final boolean verboseLogging;

    public FreshFriedFishTokenizerService() {
        this(false);
    }

    public FreshFriedFishTokenizerService(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public TokenizationResult countTokens(String text) throws TokenizerException {
        try {
            if (verboseLogging) {
                System.out.println("[TokenCounter] Counting tokens for text of length: " + text.length());
            }
            var startTime = System.currentTimeMillis();

            var jsonPayload = objectMapper.writeValueAsString(new TextRequest(text));

            var request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "*/*")
                .header("Content-Type", "application/json")
                .header("Origin", "https://claude-tokenizer.vercel.app")
                .header("Referer", "https://claude-tokenizer.vercel.app/")
                .header("User-Agent", "CCAPI-TokenCounter/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new TokenizerException("API request failed with status: " + response.statusCode() +
                                          " - " + response.body());
            }

            var jsonResponse = objectMapper.readTree(response.body());

            if (jsonResponse.has("error")) {
                throw new TokenizerException("API error: " + jsonResponse.get("error").asText());
            }

            var tokenCount = extractTokenCount(jsonResponse);
            var duration = System.currentTimeMillis() - startTime;

            var result = TokenizationResult.of(text, tokenCount, PROVIDER_INFO.defaultPayloadOverhead())
                .providerName(PROVIDER_INFO.name())
                .analysisDurationMs(duration);

            if (verboseLogging) {
                System.out.println("[TokenCounter] Token count completed: " + tokenCount + " tokens in " + duration + "ms");
            }
            return result;

        } catch (Exception e) {
            if (verboseLogging) {
                System.err.println("[TokenCounter] Failed to count tokens: " + e.getMessage());
            }
            throw new TokenizerException("Token counting failed", e);
        }
    }

    @Override
    public CompletableFuture<TokenizationResult> countTokensAsync(String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return countTokens(text);
            } catch (TokenizerException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public ProviderInfo getProviderInfo() {
        return PROVIDER_INFO;
    }

    @Override
    public boolean isHealthy() {
        try {
            countTokens("test");
            return true;
        } catch (Exception e) {
            if (verboseLogging) {
                System.err.println("[TokenCounter] Health check failed: " + e.getMessage());
            }
            return false;
        }
    }

    private int extractTokenCount(JsonNode jsonResponse) throws TokenizerException {
        if (jsonResponse.isNumber()) {
            return jsonResponse.asInt();
        }

        var fields = new String[]{"input_tokens", "count", "tokens", "tokenCount", "token_count"};
        for (var field : fields) {
            if (jsonResponse.has(field)) {
                return jsonResponse.get(field).asInt();
            }
        }

        var fieldNames = new StringBuilder();
        jsonResponse.fieldNames().forEachRemaining(field -> {
            if (fieldNames.length() > 0) fieldNames.append(", ");
            fieldNames.append(field);
        });

        throw new TokenizerException("Unable to find token count in API response. Available fields: " +
                                   fieldNames.toString());
    }

    static class TextRequest {
        public String text;

        public TextRequest(String text) {
            this.text = text;
        }
    }
}
