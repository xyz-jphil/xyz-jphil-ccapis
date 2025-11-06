package xyz.jphil.ccapis.proxy.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Anthropic API message
 * Represents a single message in a conversation
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

    /**
     * Role: "user" or "assistant"
     */
    String role;

    /**
     * Content can be either:
     * - A simple string
     * - An array of ContentBlock objects
     *
     * Jackson will handle both cases automatically
     */
    JsonNode content;

    /**
     * Create a simple user message with text
     */
    public static Message user(String text) {
        return Message.builder()
                .role("user")
                .content(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode(text))
                .build();
    }

    /**
     * Create a simple assistant message with text
     */
    public static Message assistant(String text) {
        return Message.builder()
                .role("assistant")
                .content(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode(text))
                .build();
    }

    /**
     * Extract content from this message
     * Preserves ALL content blocks without filtering
     * - Simple text: returned as-is
     * - Structured content (arrays): serialized as JSON to preserve everything
     */
    public String getTextContent() {
        if (content == null) {
            return "";
        }

        // Simple text - return as-is
        if (content.isTextual()) {
            return content.asText();
        }

        // Structured content (arrays with tool_use, tool_result, etc.)
        // Serialize the entire structure as JSON to preserve everything
        if (content.isArray()) {
            return content.toString();
        }

        // Fallback for other types - serialize as JSON
        return content.toString();
    }
}
