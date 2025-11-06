package xyz.jphil.ccapis.proxy.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Anthropic API token usage information
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicUsage {

    @JsonProperty("input_tokens")
    Integer inputTokens;

    @JsonProperty("output_tokens")
    Integer outputTokens;

    /**
     * Create usage info from input and output counts
     */
    public static AnthropicUsage of(int inputTokens, int outputTokens) {
        return AnthropicUsage.builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .build();
    }
}
