package xyz.jphil.ccapis.health;

import xyz.jphil.ccapis.CCAPIClient;
import xyz.jphil.ccapis.model.CCAPICredential;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * Selects the best account to use based on current usage and health.
 *
 * Enhanced with predictive selection (PRP-20):
 * - Uses predicted quota instead of just API-reported usage
 * - Skips accounts with predicted usage > 85% (critical threshold)
 * - Forces rotation after 15 consecutive requests on same account
 * - Prefers accounts with headroom for the upcoming request
 *
 * Selection strategy:
 * 1. Filter to active accounts only
 * 2. Filter to available accounts (not in cooldown, not auth failed)
 * 3. Filter out accounts with critical predicted usage (>85%)
 * 4. Force rotation if account used 15+ times consecutively
 * 5. Sort by: lowest predicted usage → lowest usage-to-time ratio → tier priority
 * 6. Return the best candidate
 */
public class UsageBasedAccountSelector {

    private final CCAPIClient client;
    private final AccountHealthMonitor healthMonitor;
    private final AccountRotationLogger logger;
    private String lastSelectedAccountId = null;

    public UsageBasedAccountSelector(CCAPIClient client,
                                    AccountHealthMonitor healthMonitor,
                                    AccountRotationLogger logger) {
        this.client = client;
        this.healthMonitor = healthMonitor;
        this.logger = logger;
    }

    /**
     * Select the best account from available credentials.
     * Refreshes usage for stale accounts before selection.
     *
     * Enhanced with predictive selection (PRP-20):
     * - Uses predicted quota to avoid hitting limits
     * - Forces rotation after MAX_CONSECUTIVE_REQUESTS
     * - Skips accounts approaching critical usage
     *
     * @param credentials All credentials to consider
     * @return Best credential to use, or null if none available
     */
    public CCAPICredential selectBestAccount(List<CCAPICredential> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }

        // Filter to active credentials
        var activeCredentials = credentials.stream()
            .filter(CCAPICredential::active)
            .toList();

        if (activeCredentials.isEmpty()) {
            return null;
        }

        // Refresh usage for stale accounts
        for (var cred : activeCredentials) {
            refreshUsageIfStale(cred);
        }

        // Get available accounts sorted by predicted usage
        var availableAccounts = getAvailableAccountsSortedByPredictedUsage(activeCredentials);

        // Log candidates in fine mode
        logger.logCandidatesConsidered(availableAccounts, healthMonitor);

        // Return best account (lowest predicted usage)
        var selected = availableAccounts.isEmpty() ? null : availableAccounts.get(0);

        // Track selected account for consecutive request counting
        if (selected != null) {
            lastSelectedAccountId = selected.id();
        }

        // Log selection with prediction info
        if (selected != null) {
            var health = healthMonitor.getHealth(selected.id());
            var predictedUsage = healthMonitor.getPredictedUsage(selected.id());
            var consecutiveRequests = healthMonitor.getConsecutiveRequests(selected.id());

            System.out.println(String.format(
                "[ROTATION] Selected %s (predicted: %.1f%%, consecutive: %d/%d)",
                selected.id(), predictedUsage, consecutiveRequests, QuotaPredictor.MAX_CONSECUTIVE_REQUESTS
            ));

            logger.logAccountSelection(selected, health);
        }

        // Periodically log load summary
        logger.logLoadSummary(credentials, healthMonitor);

        return selected;
    }

    /**
     * Get available accounts sorted by predicted usage and health metrics.
     *
     * Filters out:
     * - Accounts that are not available (circuit breaker)
     * - Accounts with critical predicted usage (>85%)
     * - Last selected account if it has reached MAX_CONSECUTIVE_REQUESTS
     */
    private List<CCAPICredential> getAvailableAccountsSortedByPredictedUsage(List<CCAPICredential> credentials) {
        return credentials.stream()
            .filter(cred -> {
                // Must be available (circuit breaker check)
                if (!healthMonitor.isAccountAvailable(cred.id())) {
                    return false;
                }

                // Filter out accounts with critical predicted usage
                var predictedUsage = healthMonitor.getPredictedUsage(cred.id());
                if (QuotaPredictor.isCriticalUsage(predictedUsage)) {
                    System.out.println(String.format(
                        "[ROTATION] Skipping %s (predicted usage %.1f%% > critical threshold %.1f%%)",
                        cred.id(), predictedUsage, QuotaPredictor.CRITICAL_USAGE_THRESHOLD
                    ));
                    return false;
                }

                // Force rotation if reached max consecutive requests on same account
                if (cred.id().equals(lastSelectedAccountId)) {
                    var consecutiveRequests = healthMonitor.getConsecutiveRequests(cred.id());
                    if (consecutiveRequests >= QuotaPredictor.MAX_CONSECUTIVE_REQUESTS) {
                        System.out.println(String.format(
                            "[ROTATION] Forcing rotation from %s (reached max consecutive requests: %d)",
                            cred.id(), consecutiveRequests
                        ));
                        return false;
                    }
                }

                return true;
            })
            .sorted(createPredictiveUsageComparator())
            .toList();
    }

    /**
     * Create comparator for sorting accounts by predicted usage (PRP-20):
     * 1. Predicted usage percentage (lower is better)
     * 2. Usage-to-time ratio (lower is better)
     * 3. Tier (higher is better - paid accounts preferred)
     */
    private Comparator<CCAPICredential> createPredictiveUsageComparator() {
        return Comparator
            // Primary: lowest predicted usage percentage
            .comparingDouble((CCAPICredential cred) -> {
                // Use predicted usage instead of API usage
                return healthMonitor.getPredictedUsage(cred.id());
            })
            // Secondary: lowest usage-to-time ratio
            .thenComparingDouble(cred -> {
                var health = healthMonitor.getHealth(cred.id());
                if (health == null || health.latestUsage() == null ||
                    health.latestUsage().fiveHour() == null) {
                    return 0.0;
                }

                var usage = health.latestUsage().fiveHour();
                var predictedUsage = healthMonitor.getPredictedUsage(cred.id());
                var resetTime = usage.resetsAt();

                if (resetTime == null) {
                    return predictedUsage;
                }

                // Calculate time elapsed percentage
                var now = java.time.OffsetDateTime.now();
                var fiveHoursInSeconds = 5 * 60 * 60;
                var timeUntilReset = java.time.Duration.between(now, resetTime).getSeconds();
                var elapsedSeconds = fiveHoursInSeconds - timeUntilReset;
                var timePercent = (elapsedSeconds * 100.0) / fiveHoursInSeconds;

                // Return usage-to-time ratio (lower is better)
                return timePercent > 0 ? predictedUsage / timePercent : predictedUsage;
            })
            // Tertiary: prefer higher tier accounts (paid over free)
            .thenComparingInt(cred -> -cred.tier()); // Negative to reverse order
    }

    /**
     * Refresh usage data if stale
     */
    private void refreshUsageIfStale(CCAPICredential credential) {
        if (healthMonitor.isUsageStale(credential.id())) {
            try {
                var usage = client.fetchUsage(credential);
                healthMonitor.updateUsage(credential.id(), usage);
            } catch (IOException e) {
                // Classify and record failure
                var failureType = ErrorClassifier.classify(e);
                healthMonitor.recordFailure(credential.id(), failureType);

                // Log if account became unavailable
                if (!healthMonitor.isAccountAvailable(credential.id())) {
                    logger.logAccountUnavailable(credential.id(),
                        ErrorClassifier.getDescription(failureType));
                }
            }
        }
    }

    /**
     * Force refresh usage for all accounts (useful for initial load or manual refresh)
     */
    public void refreshAllUsage(List<CCAPICredential> credentials) {
        for (var cred : credentials) {
            if (cred.active()) {
                try {
                    var usage = client.fetchUsage(cred);
                    healthMonitor.updateUsage(cred.id(), usage);
                } catch (IOException e) {
                    var failureType = ErrorClassifier.classify(e);
                    healthMonitor.recordFailure(cred.id(), failureType);
                }
            }
        }
    }
}
