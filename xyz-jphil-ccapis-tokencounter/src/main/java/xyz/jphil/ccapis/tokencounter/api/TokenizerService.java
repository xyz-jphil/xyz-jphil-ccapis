package xyz.jphil.ccapis.tokencounter.api;

import java.util.concurrent.CompletableFuture;

/**
 * Main service interface for Claude tokenization operations.
 * Provides both synchronous and asynchronous token counting capabilities.
 */
public interface TokenizerService {

    /**
     * Count tokens in the provided text synchronously.
     *
     * @param text the text to analyze
     * @return token analysis result
     * @throws TokenizerException if tokenization fails
     */
    TokenizationResult countTokens(String text) throws TokenizerException;

    /**
     * Count tokens in the provided text asynchronously.
     *
     * @param text the text to analyze
     * @return future containing token analysis result
     */
    CompletableFuture<TokenizationResult> countTokensAsync(String text);

    /**
     * Get information about this tokenizer provider.
     *
     * @return provider information
     */
    ProviderInfo getProviderInfo();

    /**
     * Check if the tokenizer service is available and operational.
     *
     * @return true if service is healthy
     */
    boolean isHealthy();
}
