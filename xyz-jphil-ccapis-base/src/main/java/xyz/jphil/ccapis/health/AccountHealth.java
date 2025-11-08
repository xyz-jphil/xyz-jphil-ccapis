package xyz.jphil.ccapis.health;

import lombok.Data;
import lombok.experimental.Accessors;
import xyz.jphil.ccapis.model.CircuitBreakerConfig;
import xyz.jphil.ccapis.model.UsageData;

import java.time.OffsetDateTime;
import java.time.Duration;

/**
 * Tracks health status and failure history for a single account.
 * Implements circuit breaker pattern to prevent overloading failing accounts.
 */
@Data
@Accessors(fluent = true, chain = true)
public class AccountHealth {

    private final String accountId;
    private AccountHealthState state = AccountHealthState.HEALTHY;

    // Failure tracking
    private int consecutiveFailures = 0;
    private FailureType lastFailureType;
    private OffsetDateTime lastFailureTime;

    // Circuit breaker timing
    private OffsetDateTime circuitOpenedAt;
    private OffsetDateTime cooldownUntil;

    // Half-open state tracking
    private int halfOpenAttempts = 0;

    // Latest usage data for quota checking
    private UsageData latestUsage;
    private OffsetDateTime usageFetchedAt;

    public AccountHealth(String accountId) {
        this.accountId = accountId;
    }

    /**
     * Record a successful request - clears failures and restores health.
     */
    public void recordSuccess() {
        this.consecutiveFailures = 0;
        this.lastFailureType = null;
        this.lastFailureTime = null;
        this.circuitOpenedAt = null;
        this.cooldownUntil = null;
        this.halfOpenAttempts = 0;
        this.state = AccountHealthState.HEALTHY;
    }

    /**
     * Record a failure and update state based on configuration.
     */
    public void recordFailure(FailureType failureType, CircuitBreakerConfig config) {
        this.consecutiveFailures++;
        this.lastFailureType = failureType;
        this.lastFailureTime = OffsetDateTime.now();

        // Determine if circuit should open
        if (consecutiveFailures >= config.failureThreshold() || failureType == FailureType.QUOTA_EXHAUSTED) {
            openCircuit(failureType, config);
        } else {
            this.state = AccountHealthState.DEGRADED;
        }
    }

    /**
     * Open the circuit and set cooldown period based on failure type.
     */
    private void openCircuit(FailureType failureType, CircuitBreakerConfig config) {
        var now = OffsetDateTime.now();
        this.state = AccountHealthState.OPEN;
        this.circuitOpenedAt = now;

        // Calculate cooldown based on failure type
        switch (failureType) {
            case QUOTA_EXHAUSTED:
                // Dynamic cooldown: until quota resets (from Usage.resetsAt)
                if (latestUsage != null && latestUsage.fiveHour() != null &&
                    latestUsage.fiveHour().resetsAt() != null) {
                    this.cooldownUntil = latestUsage.fiveHour().resetsAt();
                } else {
                    // Fallback if no usage data available
                    this.cooldownUntil = now.plusMinutes(config.rateLimitCooldownMinutes());
                }
                break;

            case RATE_LIMITED:
                this.cooldownUntil = now.plusMinutes(config.rateLimitCooldownMinutes());
                break;

            case GENERIC_ERROR:
                this.cooldownUntil = now.plusMinutes(config.genericErrorCooldownMinutes());
                break;

            default:
                this.cooldownUntil = now.plusMinutes(config.genericErrorCooldownMinutes());
        }
    }

    /**
     * Check if account is available for requests.
     */
    public boolean isAvailable() {
        return state == AccountHealthState.HEALTHY || state == AccountHealthState.DEGRADED;
    }

    /**
     * Update state based on current time and cooldown expiration.
     * Transitions OPEN -> HALF_OPEN when cooldown expires.
     */
    public void updateState(CircuitBreakerConfig config) {
        if (state == AccountHealthState.OPEN && cooldownUntil != null) {
            var now = OffsetDateTime.now();
            if (now.isAfter(cooldownUntil)) {
                // Cooldown expired, transition to half-open for testing
                this.state = AccountHealthState.HALF_OPEN;
                this.halfOpenAttempts = 0;
            }
        }
    }

    /**
     * Update usage data and check for quota exhaustion.
     * Returns true if quota is exhausted.
     */
    public boolean updateUsage(UsageData usage, CircuitBreakerConfig config) {
        this.latestUsage = usage;
        this.usageFetchedAt = OffsetDateTime.now();

        // Check if quota is exhausted (utilization >= 100)
        if (usage.fiveHour() != null && usage.fiveHour().utilization() >= 100) {
            recordFailure(FailureType.QUOTA_EXHAUSTED, config);
            return true;
        }

        return false;
    }

    /**
     * Check if usage data is stale and needs refresh.
     */
    public boolean isUsageStale(CircuitBreakerConfig config) {
        if (usageFetchedAt == null) {
            return true;
        }
        var staleDuration = Duration.between(usageFetchedAt, OffsetDateTime.now());
        return staleDuration.toMinutes() >= config.recheckUsageBeforeSelectionMinutes();
    }

    /**
     * Get time remaining until cooldown expires.
     */
    public Duration getTimeUntilCooldownExpires() {
        if (cooldownUntil == null) {
            return Duration.ZERO;
        }
        return Duration.between(OffsetDateTime.now(), cooldownUntil);
    }

    /**
     * Get human-readable status string.
     */
    public String getStatusString() {
        var sb = new StringBuilder();
        sb.append(String.format("[%s] %s", accountId, state));

        if (consecutiveFailures > 0) {
            sb.append(String.format(" (failures: %d)", consecutiveFailures));
        }

        if (state == AccountHealthState.OPEN && cooldownUntil != null) {
            var remaining = getTimeUntilCooldownExpires();
            sb.append(String.format(" - cooldown: %dm", remaining.toMinutes()));
        }

        if (latestUsage != null && latestUsage.fiveHour() != null) {
            sb.append(String.format(" - usage: %d%%", latestUsage.fiveHour().utilization()));
        }

        return sb.toString();
    }
}
