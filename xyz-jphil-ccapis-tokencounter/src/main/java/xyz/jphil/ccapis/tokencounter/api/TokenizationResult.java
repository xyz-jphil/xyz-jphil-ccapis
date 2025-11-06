package xyz.jphil.ccapis.tokencounter.api;

import lombok.*;
import lombok.experimental.Accessors;

/**
 * Result of token analysis containing detailed metrics and statistics.
 * Provides comprehensive information about text tokenization including
 * payload overhead estimation and efficiency metrics.
 */
@Data
@Builder
@Accessors(fluent = true)
@AllArgsConstructor
@NoArgsConstructor
public class TokenizationResult {

    /** Original text that was analyzed */
    String originalText;

    /** Total token count including any API payload overhead */
    int totalTokens;

    /** Estimated payload overhead in tokens */
    int estimatedPayloadOverhead;

    /** Content tokens (total minus estimated overhead) */
    int contentTokens;

    /** Word count in the original text */
    int wordCount;

    /** Character count in the original text */
    int characterCount;

    /** Total tokens per word ratio */
    double totalTokensPerWord;

    /** Content tokens per word ratio */
    double contentTokensPerWord;

    /** Total tokens per character ratio */
    double totalTokensPerCharacter;

    /** Content tokens per character ratio */
    double contentTokensPerCharacter;

    /** Provider that generated this result */
    String providerName;

    /** Timestamp when analysis was performed */
    long analysisTimestamp;

    /** Duration of the analysis in milliseconds */
    long analysisDurationMs;

    /**
     * Create a basic result with just text and token count.
     * Other metrics will be calculated automatically.
     */
    public static TokenizationResult of(String text, int totalTokens) {
        return of(text, totalTokens, 8); // Default payload overhead
    }

    /**
     * Create a result with specified payload overhead.
     * Other metrics will be calculated automatically.
     */
    public static TokenizationResult of(String text, int totalTokens, int payloadOverhead) {
        var words = text.trim().split("\\s+");
        var wordCount = text.trim().isEmpty() ? 0 : words.length;
        var charCount = text.length();
        var contentTokens = Math.max(0, totalTokens - payloadOverhead);

        return TokenizationResult.builder()
            .originalText(text)
            .totalTokens(totalTokens)
            .estimatedPayloadOverhead(payloadOverhead)
            .contentTokens(contentTokens)
            .wordCount(wordCount)
            .characterCount(charCount)
            .totalTokensPerWord(wordCount > 0 ? (double) totalTokens / wordCount : 0.0)
            .contentTokensPerWord(wordCount > 0 ? (double) contentTokens / wordCount : 0.0)
            .totalTokensPerCharacter(charCount > 0 ? (double) totalTokens / charCount : 0.0)
            .contentTokensPerCharacter(charCount > 0 ? (double) contentTokens / charCount : 0.0)
            .analysisTimestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * Get a truncated version of the original text for display purposes.
     */
    public String displayText() {
        return displayText(100);
    }

    /**
     * Get a truncated version of the original text with specified max length.
     */
    public String displayText(int maxLength) {
        if (originalText == null) return "";
        return originalText.length() > maxLength ?
            originalText.substring(0, maxLength) + "..." : originalText;
    }
}
