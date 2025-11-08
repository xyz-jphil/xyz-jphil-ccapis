package xyz.jphil.ccapis.proxy.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 * Anthropic API content block
 * Represents a piece of content in a message (text, image, tool_use)
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentBlock {

    /**
     * Type of content block: "text", "image", or "tool_use"
     */
    String type;

    /**
     * Text content (for type="text")
     */
    String text;

    /**
     * Image source (for type="image")
     */
    @JsonProperty("source")
    ImageSource source;

    /**
     * Tool use ID (for type="tool_use")
     */
    String id;

    /**
     * Tool name (for type="tool_use")
     */
    String name;

    /**
     * Tool input parameters (for type="tool_use")
     */
    Map<String, Object> input;

    /**
     * Image source information
     */
    @Value
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageSource {
        String type;  // "base64"

        @JsonProperty("media_type")
        String mediaType;  // "image/jpeg", "image/png", etc.

        String data;  // base64 encoded image data
    }

    /**
     * Create a simple text content block
     */
    public static ContentBlock text(String text) {
        return ContentBlock.builder()
                .type("text")
                .text(text)
                .build();
    }

    /**
     * Create a tool_use content block from ToolUse
     */
    public static ContentBlock toolUse(ToolUse toolUse) {
        return ContentBlock.builder()
                .type("tool_use")
                .id(toolUse.getId())
                .name(toolUse.getName())
                .input(toolUse.getInput())
                .build();
    }
}
