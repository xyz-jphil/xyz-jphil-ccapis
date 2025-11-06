package xyz.jphil.ccapis.proxy.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Anthropic Messages API request format
 * POST /v1/messages
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicRequest {

    /**
     * Model to use (e.g., "claude-3-opus-20240229", "claude-3-sonnet-20240229")
     */
    String model;

    /**
     * List of messages in the conversation
     */
    List<Message> messages;

    /**
     * System prompt (optional)
     * Can be either:
     * - A simple string: "You are a helpful assistant"
     * - An array of content blocks: [{"type":"text","text":"You are..."}]
     */
    JsonNode system;

    /**
     * Maximum tokens to generate
     */
    @JsonProperty("max_tokens")
    Integer maxTokens;

    /**
     * Temperature (NOT SUPPORTED by CCAPI - will be ignored)
     * Included for API compatibility
     */
    Double temperature;

    /**
     * Whether to stream the response
     */
    Boolean stream;

    /**
     * Additional metadata (optional)
     */
    Object metadata;

    /**
     * Stop sequences (optional)
     */
    @JsonProperty("stop_sequences")
    List<String> stopSequences;

    /**
     * Top-k sampling (optional)
     */
    @JsonProperty("top_k")
    Integer topK;

    /**
     * Top-p sampling (optional)
     */
    @JsonProperty("top_p")
    Double topP;

    /**
     * Tool definitions (optional)
     * Array of tool definitions that Claude can use
     */
    List<JsonNode> tools;

    /**
     * Tool choice configuration (optional)
     * Controls how Claude uses tools: "auto", "any", "none", or {"type":"tool","name":"tool_name"}
     */
    @JsonProperty("tool_choice")
    JsonNode toolChoice;

    /**
     * Extract system prompt as a string
     * Handles both string and array formats
     */
    public String getSystemPrompt() {
        if (system == null) {
            return null;
        }

        // If it's a simple string
        if (system.isTextual()) {
            return system.asText();
        }

        // If it's an array of content blocks
        if (system.isArray()) {
            var result = new StringBuilder();
            for (var block : system) {
                if (block.has("text")) {
                    if (result.length() > 0) {
                        result.append("\n\n");
                    }
                    result.append(block.get("text").asText());
                }
            }
            return result.toString();
        }

        return null;
    }
}
