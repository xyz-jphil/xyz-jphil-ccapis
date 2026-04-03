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
 *
 * Enhanced with predictive quota tracking (PRP-20):
 * - Tracks predicted quota usage alongside API-reported usage
 * - Enables proactive account rotation before hitting limits
 * - Validates PRP-19 formula accuracy in production
 */
public class AccountHealthMonitor {

    private final Map<String, AccountHealth> healthMap = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig config;
    private final PredictiveUsageTracker predictiveTracker;

    public AccountHealthMonitor(CircuitBreakerConfig config) {
        this.config = config != null ? config : new CircuitBreakerConfig();
        this.predictiveTracker = new PredictiveUsageTracker();
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
     * Syncs with predictive tracker and logs comparison.
     */
    public void updateUsage(String accountId, UsageData usage) {
        getHealth(accountId).updateUsage(usage, config);

        // Sync predictive tracker with actual API usage
        if (usage != null && usage.fiveHour() != null) {
            var apiUsage = usage.fiveHour().utilization();
            predictiveTracker.syncWithApiUsage(accountId, apiUsage);

            // Log comparison for validation (info level)
            var comparison = predictiveTracker.getComparisonLog(accountId, apiUsage);
            System.out.println("[PREDICT] " + accountId + ": " + comparison);
        }
    }

    /**
     * Record predicted quota usage before making a request.
     * Call this before sendMessage() to track predicted quota.
     *
     * @param accountId Account identifier
     * @param inputTokens Estimated input tokens
     * @param outputTokens Estimated output tokens
     * @return Predicted quota increase percentage
     */
    public double recordPredictedRequest(String accountId, int inputTokens, int outputTokens) {
        var quotaIncrease = QuotaPredictor.predictQuotaUsage(inputTokens, outputTokens);
        predictiveTracker.recordPredictedUsage(accountId, quotaIncrease);

        // Log prediction (fine level)
        var health = getHealth(accountId);
        var currentApiUsage = health.latestUsage() != null && health.latestUsage().fiveHour() != null
            ? health.latestUsage().fiveHour().utilization()
            : 0.0;

        var predicted = predictiveTracker.getPredictedUsage(accountId);
        System.out.println(String.format(
            "[PREDICT] %s: Request will use ~%.1f%% quota (API: %.1f%%, predicted after: %.1f%%)",
            accountId, quotaIncrease, currentApiUsage, predicted
        ));

        return quotaIncrease;
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

    /**
     * Get predictive usage tracker.
     */
    public PredictiveUsageTracker getPredictiveTracker() {
        return predictiveTracker;
    }

    /**
     * Get predicted usage percentage for an account.
     *
     * @param accountId Account identifier
     * @return Predicted usage percentage
     */
    public double getPredictedUsage(String accountId) {
        return predictiveTracker.getPredictedUsage(accountId);
    }

    /**
     * Get number of consecutive requests for an account.
     *
     * @param accountId Account identifier
     * @return Request count since last API sync
     */
    public int getConsecutiveRequests(String accountId) {
        return predictiveTracker.getRequestCount(accountId);
    }
}
