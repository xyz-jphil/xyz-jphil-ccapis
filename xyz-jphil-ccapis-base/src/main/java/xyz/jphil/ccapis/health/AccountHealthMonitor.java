package xyz.jphil.ccapis.health;

import xyz.jphil.ccapis.model.CCAPICredential;
import xyz.jphil.ccapis.model.CircuitBreakerConfig;
import xyz.jphil.ccapis.model.UsageData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Centralized monitor for tracking health of all CCAPI accounts.
 * Thread-safe for concurrent access.
 */
public class AccountHealthMonitor {

    private final Map<String, AccountHealth> healthMap = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig config;

    public AccountHealthMonitor(CircuitBreakerConfig config) {
        this.config = config != null ? config : new CircuitBreakerConfig();
    }

    /**
     * Get or create health tracker for an account.
     */
    public AccountHealth getHealth(String accountId) {
        return healthMap.computeIfAbsent(accountId, AccountHealth::new);
    }

    /**
     * Record successful request for an account.
     */
    public void recordSuccess(String accountId) {
        getHealth(accountId).recordSuccess();
    }

    /**
     * Record failure for an account.
     */
    public void recordFailure(String accountId, FailureType failureType) {
        getHealth(accountId).recordFailure(failureType, config);
    }

    /**
     * Update usage data for an account and check quota exhaustion.
     */
    public void updateUsage(String accountId, UsageData usage) {
        getHealth(accountId).updateUsage(usage, config);
    }

    /**
     * Check if account is available for requests.
     */
    public boolean isAccountAvailable(String accountId) {
        if (!config.enabled()) {
            return true; // Circuit breaker disabled
        }

        var health = getHealth(accountId);
        health.updateState(config);
        return health.isAvailable();
    }

    /**
     * Get all available accounts from a list, sorted by health and usage.
     * Returns accounts in priority order: HEALTHY with low usage first.
     */
    public List<CCAPICredential> getAvailableAccounts(List<CCAPICredential> credentials) {
        if (!config.enabled()) {
            return credentials; // Circuit breaker disabled, return all
        }

        return credentials.stream()
                .filter(cred -> {
                    var health = getHealth(cred.id());
                    health.updateState(config);
                    return health.isAvailable();
                })
                .sorted((a, b) -> {
                    var healthA = getHealth(a.id());
                    var healthB = getHealth(b.id());

                    // Primary sort: by state (HEALTHY before DEGRADED)
                    var stateCompare = healthA.state().compareTo(healthB.state());
                    if (stateCompare != 0) {
                        return stateCompare;
                    }

                    // Secondary sort: by usage (lower utilization first)
                    var usageA = healthA.latestUsage() != null &&
                                 healthA.latestUsage().fiveHour() != null ?
                                 healthA.latestUsage().fiveHour().utilization() : 0.0;
                    var usageB = healthB.latestUsage() != null &&
                                 healthB.latestUsage().fiveHour() != null ?
                                 healthB.latestUsage().fiveHour().utilization() : 0.0;

                    return Double.compare(usageA, usageB);
                })
                .collect(Collectors.toList());
    }

    /**
     * Get best available account (lowest usage, healthiest).
     * Returns null if no accounts available.
     */
    public CCAPICredential selectBestAccount(List<CCAPICredential> credentials) {
        var available = getAvailableAccounts(credentials);
        return available.isEmpty() ? null : available.get(0);
    }

    /**
     * Check if usage data is stale for an account.
     */
    public boolean isUsageStale(String accountId) {
        return getHealth(accountId).isUsageStale(config);
    }

    /**
     * Get health status summary for all tracked accounts.
     */
    public String getHealthSummary() {
        var sb = new StringBuilder();
        sb.append("=== Account Health Summary ===\n");

        if (!config.enabled()) {
            sb.append("Circuit breaker: DISABLED\n");
            return sb.toString();
        }

        sb.append(String.format("Circuit breaker: ENABLED (threshold: %d failures)\n",
                config.failureThreshold()));

        healthMap.forEach((accountId, health) -> {
            health.updateState(config);
            sb.append(health.getStatusString()).append("\n");
        });

        return sb.toString();
    }

    /**
     * Reset health for all accounts (useful for testing).
     */
    public void resetAll() {
        healthMap.clear();
    }

    /**
     * Reset health for specific account.
     */
    public void reset(String accountId) {
        healthMap.remove(accountId);
    }

    /**
     * Get configuration.
     */
    public CircuitBreakerConfig getConfig() {
        return config;
    }
}
