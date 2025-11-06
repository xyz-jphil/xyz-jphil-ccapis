package xyz.jphil.ccapis.tokencounter.api;

import lombok.*;
import lombok.experimental.Accessors;

/**
 * Information about a tokenizer service provider.
 */
@Data
@Builder
@Accessors(fluent = true)
@AllArgsConstructor
@NoArgsConstructor
public class ProviderInfo {

    /** Name of the provider */
    String name;

    /** Version of the provider implementation */
    String version;

    /** Description of the provider */
    String description;

    /** Base URL or endpoint information */
    String endpoint;

    /** Whether this provider requires internet connectivity */
    boolean requiresInternet;

    /** Estimated default payload overhead in tokens */
    int defaultPayloadOverhead;

    /**
     * Create provider info for Anthropic Claude tokenizer service.
     */
    public static ProviderInfo anthropicClaudeTokenizer() {
        return ProviderInfo.builder()
            .name("AnthropicClaudeTokenizer")
            .version("1.0")
            .description("Anthropic Claude tokenization service via claude-tokenizer.vercel.app")
            .endpoint("https://claude-tokenizer.vercel.app/api")
            .requiresInternet(true)
            .defaultPayloadOverhead(8)
            .build();
    }
}
