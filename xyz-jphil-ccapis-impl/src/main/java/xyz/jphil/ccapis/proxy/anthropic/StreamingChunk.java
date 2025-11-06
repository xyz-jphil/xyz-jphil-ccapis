package xyz.jphil.ccapis.proxy.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Anthropic API streaming response chunk
 *
 * Streaming events:
 * - message_start: Initial message metadata
 * - content_block_start: Start of a content block
 * - content_block_delta: Incremental text
 * - content_block_stop: End of a content block
 * - message_delta: Message-level updates (stop_reason, usage)
 * - message_stop: End of message
 * - ping: Keep-alive
 * - error: Error event
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamingChunk {

    /**
     * Event type
     */
    String type;

    /**
     * Message metadata (for message_start)
     */
    MessageData message;

    /**
     * Content block index (for content_block_start)
     */
    Integer index;

    /**
     * Content block info (for content_block_start)
     */
    @JsonProperty("content_block")
    ContentBlock contentBlock;

    /**
     * Delta update (for content_block_delta)
     */
    Delta delta;

    /**
     * Usage update (for message_delta)
     */
    AnthropicUsage usage;

    /**
     * Error information (for error events)
     */
    ErrorData error;

    /**
     * Message metadata structure
     */
    @Value
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MessageData {
        String id;
        String type;
        String role;
        String model;
        AnthropicUsage usage;

        @JsonProperty("stop_reason")
        String stopReason;

        @JsonProperty("stop_sequence")
        String stopSequence;
    }

    /**
     * Delta update structure
     */
    @Value
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delta {
        String type;
        String text;

        @JsonProperty("stop_reason")
        String stopReason;

        @JsonProperty("stop_sequence")
        String stopSequence;
    }

    /**
     * Error information
     */
    @Value
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorData {
        String type;
        String message;
    }

    /**
     * Create a message_start event
     */
    public static StreamingChunk messageStart(String id, String model) {
        return StreamingChunk.builder()
                .type("message_start")
                .message(MessageData.builder()
                        .id(id)
                        .type("message")
                        .role("assistant")
                        .model(model)
                        .build())
                .build();
    }

    /**
     * Create a content_block_start event
     */
    public static StreamingChunk contentBlockStart(int index) {
        return StreamingChunk.builder()
                .type("content_block_start")
                .index(index)
                .contentBlock(ContentBlock.builder()
                        .type("text")
                        .text("")
                        .build())
                .build();
    }

    /**
     * Create a content_block_delta event with text
     */
    public static StreamingChunk contentBlockDelta(int index, String text) {
        return StreamingChunk.builder()
                .type("content_block_delta")
                .index(index)
                .delta(Delta.builder()
                        .type("text_delta")
                        .text(text)
                        .build())
                .build();
    }

    /**
     * Create a content_block_stop event
     */
    public static StreamingChunk contentBlockStop(int index) {
        return StreamingChunk.builder()
                .type("content_block_stop")
                .index(index)
                .build();
    }

    /**
     * Create a message_delta event with stop reason
     */
    public static StreamingChunk messageDelta(String stopReason, AnthropicUsage usage) {
        return StreamingChunk.builder()
                .type("message_delta")
                .delta(Delta.builder()
                        .stopReason(stopReason)
                        .build())
                .usage(usage)
                .build();
    }

    /**
     * Create a message_stop event
     */
    public static StreamingChunk messageStop() {
        return StreamingChunk.builder()
                .type("message_stop")
                .build();
    }

    /**
     * Create a ping event
     */
    public static StreamingChunk ping() {
        return StreamingChunk.builder()
                .type("ping")
                .build();
    }

    /**
     * Create an error event
     */
    public static StreamingChunk error(String type, String message) {
        return StreamingChunk.builder()
                .type("error")
                .error(ErrorData.builder()
                        .type(type)
                        .message(message)
                        .build())
                .build();
    }
}
