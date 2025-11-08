package xyz.jphil.ccapis.health;

import java.io.IOException;

/**
 * Classifies exceptions and error messages into failure types.
 * Used to determine appropriate circuit breaker response.
 */
public class ErrorClassifier {

    /**
     * Classify an IOException into a FailureType.
     * Analyzes exception message and type to determine the failure category.
     */
    public static FailureType classify(IOException exception) {
        if (exception == null) {
            return FailureType.GENERIC_ERROR;
        }

        var message = exception.getMessage();
        if (message == null) {
            return FailureType.GENERIC_ERROR;
        }

        var lowerMessage = message.toLowerCase();

        // Check for rate limiting (HTTP 429 or rate limit messages)
        if (lowerMessage.contains("http 429") ||
            lowerMessage.contains("rate limit") ||
            lowerMessage.contains("too many requests")) {
            return FailureType.RATE_LIMITED;
        }

        // Check for quota exhaustion
        if (lowerMessage.contains("quota") ||
            lowerMessage.contains("limit exceeded") ||
            lowerMessage.contains("usage limit")) {
            return FailureType.QUOTA_EXHAUSTED;
        }

        // Default to generic error
        return FailureType.GENERIC_ERROR;
    }

    /**
     * Classify based on HTTP status code and error body.
     */
    public static FailureType classify(int httpStatusCode, String errorBody) {
        // HTTP 429 = Rate Limited
        if (httpStatusCode == 429) {
            return FailureType.RATE_LIMITED;
        }

        // HTTP 402 = Payment Required (quota exhausted)
        if (httpStatusCode == 402) {
            return FailureType.QUOTA_EXHAUSTED;
        }

        // Check error body for specific messages
        if (errorBody != null) {
            var lowerBody = errorBody.toLowerCase();

            if (lowerBody.contains("rate limit") || lowerBody.contains("too many requests")) {
                return FailureType.RATE_LIMITED;
            }

            if (lowerBody.contains("quota") || lowerBody.contains("usage limit") ||
                lowerBody.contains("limit exceeded")) {
                return FailureType.QUOTA_EXHAUSTED;
            }
        }

        // Other errors: 401 (auth), 403 (forbidden), 5xx (server), network errors
        return FailureType.GENERIC_ERROR;
    }

    /**
     * Check if error message indicates a transient failure (worth retrying after cooldown).
     */
    public static boolean isTransient(IOException exception) {
        var failureType = classify(exception);
        // Quota exhaustion and rate limiting are transient (will recover after cooldown)
        // Generic errors might be permanent (auth failure, invalid request)
        return failureType == FailureType.QUOTA_EXHAUSTED ||
               failureType == FailureType.RATE_LIMITED;
    }
}
