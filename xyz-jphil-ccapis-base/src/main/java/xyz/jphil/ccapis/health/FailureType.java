package xyz.jphil.ccapis.health;

/**
 * Types of failures that affect account health.
 * Different failure types have different cooldown strategies.
 */
public enum FailureType {
    /**
     * Account quota is exhausted (utilization >= 100%).
     * Cooldown: Until Usage.resetsAt (dynamic)
     */
    QUOTA_EXHAUSTED,

    /**
     * Rate limiting error (HTTP 429 or rate limit message).
     * Cooldown: Fixed period (default 10 minutes)
     */
    RATE_LIMITED,

    /**
     * Generic errors: network, auth, server errors, etc.
     * Cooldown: Shorter fixed period (default 5 minutes)
     */
    GENERIC_ERROR,

    /**
     * Successful request - clears failure state.
     */
    SUCCESS
}
