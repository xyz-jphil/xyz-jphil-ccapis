package xyz.jphil.ccapis.model;

import lombok.Data;
import lombok.experimental.Accessors;
import jakarta.xml.bind.annotation.*;

/**
 * Global circuit breaker configuration for account health monitoring.
 * Can be specified in CCAPIsCredentials.xml as optional element.
 * All settings have sensible defaults if not specified.
 *
 * Purpose: Prevent overloading accounts with quota exhaustion or repeated failures.
 * Strategy:
 * - Quota exhaustion: Dynamic cooldown based on Usage.resetsAt from API
 * - Rate limiting: Fixed cooldown (configurable, default 10 minutes)
 * - Generic errors: Shorter cooldown with failure threshold
 */
@Data
@Accessors(fluent = true, chain = true)
@XmlAccessorType(XmlAccessType.FIELD)
public class CircuitBreakerConfig {

    /**
     * Number of consecutive failures before opening circuit (suspending account).
     * Default: 3 failures
     */
    @XmlAttribute(name = "failureThreshold")
    private Integer failureThreshold;

    /**
     * Cooldown period in minutes after rate limiting error (HTTP 429).
     * Default: 10 minutes
     */
    @XmlAttribute(name = "rateLimitCooldownMinutes")
    private Integer rateLimitCooldownMinutes;

    /**
     * Cooldown period in minutes after generic errors (network, auth, etc).
     * Default: 5 minutes
     */
    @XmlAttribute(name = "genericErrorCooldownMinutes")
    private Integer genericErrorCooldownMinutes;

    /**
     * Number of retry attempts when circuit is in half-open state.
     * Default: 1 attempt
     */
    @XmlAttribute(name = "halfOpenRetryCount")
    private Integer halfOpenRetryCount;

    /**
     * Frequency in minutes to refresh usage data before account selection.
     * Ensures quota/usage information is fresh for smart decisions.
     * Default: 5 minutes
     */
    @XmlAttribute(name = "recheckUsageBeforeSelectionMinutes")
    private Integer recheckUsageBeforeSelectionMinutes;

    /**
     * Enable/disable circuit breaker globally.
     * Default: true (enabled)
     */
    @XmlAttribute(name = "enabled")
    private Boolean enabled;

    // Getters with defaults

    public int failureThreshold() {
        return failureThreshold != null ? failureThreshold : 3;
    }

    public int rateLimitCooldownMinutes() {
        return rateLimitCooldownMinutes != null ? rateLimitCooldownMinutes : 10;
    }

    public int genericErrorCooldownMinutes() {
        return genericErrorCooldownMinutes != null ? genericErrorCooldownMinutes : 5;
    }

    public int halfOpenRetryCount() {
        return halfOpenRetryCount != null ? halfOpenRetryCount : 1;
    }

    public int recheckUsageBeforeSelectionMinutes() {
        return recheckUsageBeforeSelectionMinutes != null ? recheckUsageBeforeSelectionMinutes : 5;
    }

    public boolean enabled() {
        return enabled != null ? enabled : true;
    }
}
