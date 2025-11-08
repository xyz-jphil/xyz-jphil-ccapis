package xyz.jphil.ccapis.health;

/**
 * Health states for circuit breaker pattern.
 * Based on Martin Fowler's Circuit Breaker pattern.
 */
public enum AccountHealthState {
    /**
     * Account is healthy and available for requests.
     * No recent failures or quota issues.
     */
    HEALTHY,

    /**
     * Account has some failures but still accepting requests.
     * Monitoring closely for threshold breach.
     */
    DEGRADED,

    /**
     * Circuit is open - account is suspended due to:
     * - Quota exhaustion (utilization >= 100%)
     * - Consecutive failures exceeding threshold
     * - Rate limiting errors
     * Account will not accept requests until cooldown expires.
     */
    OPEN,

    /**
     * Testing if account has recovered after cooldown.
     * Limited requests allowed to verify health restoration.
     */
    HALF_OPEN
}
