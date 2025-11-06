package xyz.jphil.ccapis.oauth;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized configuration for OAuth API calls.
 *
 * This class manages all the complex header requirements, model compatibility,
 * and API-specific configurations so clients don't need to worry about them.
 *
 * IMPORTANT: These configurations may change as Anthropic updates their API.
 * All changes should be made HERE, not scattered across the codebase.
 */
public class OAuthApiConfig {

    // ========================================================================
    // HEADER CONSTANTS
    // ========================================================================

    // API Version - required for all Anthropic API calls
    private static final String
        ANTHROPIC_VERSION = "2023-06-01";

    // Beta features - required for OAuth authentication (as of 2025-11-01)
    // NOTE: May be removed once OAuth is officially released
    private static final String
        BETA_CLAUDE_CODE = "claude-code-20250219",
        BETA_OAUTH = "oauth-2025-04-20",
        BETA_THINKING = "interleaved-thinking-2025-05-14",
        BETA_TOOL_STREAMING = "fine-grained-tool-streaming-2025-05-14";

    private static final String ANTHROPIC_BETA_FEATURES = String.join(",",
        BETA_CLAUDE_CODE,
        BETA_OAUTH,
        BETA_THINKING,
        BETA_TOOL_STREAMING
    );

    // ========================================================================
    // MODEL CONSTANTS
    // ========================================================================

    /**
     * Models and their OAuth compatibility status.
     */
    public static class Models {
        // Verified working with OAuth
        public static final String HAIKU_3_5 = "claude-3-5-haiku-20241022";

        // Known NOT to work with OAuth (returns "only authorized for Claude Code")
        // public static final String SONNET_3_5 = "claude-3-5-sonnet-20241022";

        // Not yet tested - add here as we verify
        // public static final String OPUS_4 = "claude-opus-4-20250514";
        // public static final String SONNET_4 = "claude-sonnet-4-20250514";
    }

    // ========================================================================
    // COMPULSORY HEADERS (Always Required)
    // ========================================================================

    /**
     * Get compulsory headers required for ALL OAuth API calls.
     * These headers are always included regardless of endpoint or model.
     */
    private static Map<String, String> getCompulsoryHeaders() {
        var headers = new HashMap<String, String>();
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        headers.put("anthropic-beta", ANTHROPIC_BETA_FEATURES);
        return headers;
    }

    // ========================================================================
    // MODEL-SPECIFIC HEADERS (Conditional)
    // ========================================================================

    /**
     * Get model-specific headers if any are required.
     *
     * @param model Model identifier
     * @return Additional headers for this model, or empty map if none
     */
    private static Map<String, String> getModelSpecificHeaders(String model) {
        var headers = new HashMap<String, String>();

        // As of 2025-11-01, no model-specific headers are needed
        // Add here when we discover model-specific requirements:
        //
        // if (Models.SONNET_4.equals(model)) {
        //     headers.put("some-sonnet-header", "value");
        // }

        return headers;
    }

    // ========================================================================
    // ENDPOINT-SPECIFIC CONFIGURATIONS
    // ========================================================================

    /**
     * Get headers for Messages API (/v1/messages).
     *
     * @param model Model to use (can be null to skip model-specific headers)
     * @return Complete headers for Messages API
     */
    public static Map<String, String> getMessagesHeaders(String model) {
        var headers = new HashMap<String, String>();
        headers.putAll(getCompulsoryHeaders());
        if (model != null) {
            headers.putAll(getModelSpecificHeaders(model));
        }
        return headers;
    }

    /**
     * Get headers for Messages API with default model.
     */
    public static Map<String, String> getMessagesHeaders() {
        return getMessagesHeaders(null);
    }

    /**
     * Get headers for Token Counting API (/v1/messages/count_tokens).
     *
     * IMPORTANT: Same as Messages API headers (discovered after debugging).
     * Initially thought it didn't need beta headers, but it DOES.
     *
     * @param model Model to use (can be null to skip model-specific headers)
     * @return Complete headers for Token Counting API
     */
    public static Map<String, String> getTokenCountHeaders(String model) {
        // Token counting requires same headers as Messages API
        return getMessagesHeaders(model);
    }

    /**
     * Get headers for Token Counting API with default model.
     */
    public static Map<String, String> getTokenCountHeaders() {
        return getTokenCountHeaders(null);
    }

    // ========================================================================
    // MODEL UTILITIES
    // ========================================================================

    /**
     * Get default model for OAuth API calls.
     * Returns the model most likely to work with OAuth.
     */
    public static String getDefaultModel() {
        return Models.HAIKU_3_5;
    }

    /**
     * Check if a model is known to work with OAuth.
     *
     * @param model Model identifier
     * @return true if model is verified to work with OAuth
     */
    public static boolean isModelSupported(String model) {
        return Models.HAIKU_3_5.equals(model);
        // Add more as we verify them:
        // || Models.OPUS_4.equals(model)
        // || Models.SONNET_4.equals(model)
    }

    /**
     * Get supported models list (for display/validation).
     */
    public static String[] getSupportedModels() {
        return new String[] {
            Models.HAIKU_3_5
            // Add more as verified
        };
    }

    // ========================================================================
    // CONFIGURATION NOTES
    // ========================================================================

    /**
     * Configuration notes for future reference:
     *
     * 1. ANTHROPIC_VERSION: Currently "2023-06-01", may need updating
     *
     * 2. ANTHROPIC_BETA_FEATURES: Required for OAuth as of 2025-11-01
     *    - May be removed once OAuth is officially released
     *    - Check Anthropic docs if you get 401 errors
     *
     * 3. Model Support:
     *    - Haiku 3.5: ✅ Works with OAuth
     *    - Sonnet 3.5: ❌ "only authorized for Claude Code" error
     *    - Other models: Not yet tested
     *
     * 4. Token Counting Quirks:
     *    - DOES require anthropic-beta headers (not obvious!)
     *    - Does NOT accept max_tokens field (returns 400 error)
     *    - Otherwise same request format as Messages API
     *
     * 5. Rate Limiting:
     *    - Token counting is FREE but still rate-limited
     *    - Messages API consumes quota
     *    - HTTP 429 = rate limit exceeded (not auth error!)
     *
     * 6. Header Evolution:
     *    - These headers might change or be removed
     *    - Always check Anthropic's latest API docs
     *    - Update this file when API changes
     */
}
