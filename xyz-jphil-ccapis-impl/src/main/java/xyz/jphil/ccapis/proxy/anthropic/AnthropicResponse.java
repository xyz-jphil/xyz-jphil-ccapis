package xyz.jphil.ccapis.proxy.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Anthropic Messages API response format
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicResponse {

    /**
     * Unique message ID
     */
    String id;

    /**
     * Type of object: "message"
     */
    String type;

    /**
     * Role of the message: "assistant"
     */
    String role;

    /**
     * Model that generated the response
     */
    String model;

    /**
     * Content blocks in the response
     */
    List<ContentBlock> content;

    /**
     * Reason why the response stopped
     * "end_turn", "max_tokens", "stop_sequence"
     */
    @JsonProperty("stop_reason")
    String stopReason;

    /**
     * The stop sequence that triggered completion, if any
     */
    @JsonProperty("stop_sequence")
    String stopSequence;

    /**
     * Token usage information
     */
    AnthropicUsage usage;

    /**
     * Create a simple text response
     */
    public static AnthropicResponse create(String id, String model, String text, AnthropicUsage usage) {
        return AnthropicResponse.builder()
                .id(id)
                .type("message")
                .role("assistant")
                .model(model)
                .content(List.of(ContentBlock.text(text)))
                .stopReason("end_turn")
                .usage(usage)
                .build();
    }
}
