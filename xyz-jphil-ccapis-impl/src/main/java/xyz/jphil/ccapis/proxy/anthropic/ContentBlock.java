package xyz.jphil.ccapis.proxy.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Anthropic API content block
 * Represents a piece of content in a message (text, image, etc.)
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentBlock {

    /**
     * Type of content block: "text" or "image"
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
}
