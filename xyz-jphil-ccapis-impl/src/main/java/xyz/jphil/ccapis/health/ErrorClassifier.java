package xyz.jphil.ccapis.health;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Classifies exceptions into failure types for circuit breaker logic.
 */
public class ErrorClassifier {

    /**
     * Classify an IOException into a FailureType
     */
    public static FailureType classify(IOException exception) {
        var message = exception.getMessage();
        if (message == null) {
            return FailureType.GENERIC_ERROR;
        }

        var lowerMessage = message.toLowerCase();

        // Check for quota exhausted (utilization >= 100% or explicit quota error)
        // Map to QUOTA_EXHAUSTED to trigger dynamic cooldown based on Usage.resetsAt
        if (lowerMessage.contains("exceeded_limit") ||
            lowerMessage.contains("quota") ||
            lowerMessage.contains("usage limit")) {
            return FailureType.QUOTA_EXHAUSTED;
        }

        // Check for rate limiting (HTTP 429)
        // Map to RATE_LIMITED to trigger fixed cooldown period
        if (lowerMessage.contains("429") ||
            lowerMessage.contains("too many requests") ||
            lowerMessage.contains("rate_limit_error")) {
            return FailureType.RATE_LIMITED;
        }

        // All other errors (auth, network, server) map to GENERIC_ERROR
        // Uses shorter cooldown period with failure threshold

        return FailureType.GENERIC_ERROR;
    }

    /**
     * Get human-readable description of failure type
     */
    public static String getDescription(FailureType type) {
        return switch (type) {
            case QUOTA_EXHAUSTED -> "Quota exhausted";
            case RATE_LIMITED -> "Rate limited";
            case GENERIC_ERROR -> "Generic error";
            case SUCCESS -> "Success";
        };
    }
}
