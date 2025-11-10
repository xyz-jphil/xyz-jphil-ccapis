package xyz.jphil.ccapis.health;

import xyz.jphil.ccapis.CCAPIClient;
import xyz.jphil.ccapis.model.CCAPICredential;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * Selects the best account to use based on current usage and health.
 * Selection strategy:
 * 1. Filter to active accounts only
 * 2. Filter to available accounts (not in cooldown, not auth failed)
 * 3. Sort by: lowest usage % → lowest usage-to-time ratio → tier priority
 * 4. Return the best candidate
 */
public class UsageBasedAccountSelector {

    private final CCAPIClient client;
    private final AccountHealthMonitor healthMonitor;
    private final AccountRotationLogger logger;

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

        // Get available accounts sorted by usage
        var availableAccounts = getAvailableAccountsSortedByUsage(activeCredentials);

        // Log candidates in fine mode
        logger.logCandidatesConsidered(availableAccounts, healthMonitor);

        // Return best account (lowest usage)
        var selected = availableAccounts.isEmpty() ? null : availableAccounts.get(0);

        // Log selection
        var health = selected != null ? healthMonitor.getHealth(selected.id()) : null;
        logger.logAccountSelection(selected, health);

        // Periodically log load summary
        logger.logLoadSummary(credentials, healthMonitor);

        return selected;
    }

    /**
     * Get available accounts sorted by usage and health metrics
     */
    private List<CCAPICredential> getAvailableAccountsSortedByUsage(List<CCAPICredential> credentials) {
        return credentials.stream()
            .filter(cred -> healthMonitor.isAccountAvailable(cred.id()))
            .sorted(createUsageComparator())
            .toList();
    }

    /**
     * Create comparator for sorting accounts by:
     * 1. Usage percentage (lower is better)
     * 2. Usage-to-time ratio (lower is better)
     * 3. Tier (higher is better - paid accounts preferred)
     */
    private Comparator<CCAPICredential> createUsageComparator() {
        return Comparator
            // Primary: lowest usage percentage
            .comparingDouble((CCAPICredential cred) -> {
                var health = healthMonitor.getHealth(cred.id());
                if (health != null && health.latestUsage() != null && health.latestUsage().fiveHour() != null) {
                    return health.latestUsage().fiveHour().utilization();
                }
                return 0.0;
            })
            // Secondary: lowest usage-to-time ratio
            .thenComparingDouble(cred -> {
                var health = healthMonitor.getHealth(cred.id());
                if (health == null || health.latestUsage() == null ||
                    health.latestUsage().fiveHour() == null) {
                    return 0.0;
                }

                var usage = health.latestUsage().fiveHour();
                var utilization = usage.utilization();
                var resetTime = usage.resetsAt();

                if (resetTime == null) {
                    return utilization;
                }

                // Calculate time elapsed percentage
                var now = java.time.OffsetDateTime.now();
                var fiveHoursInSeconds = 5 * 60 * 60;
                var timeUntilReset = java.time.Duration.between(now, resetTime).getSeconds();
                var elapsedSeconds = fiveHoursInSeconds - timeUntilReset;
                var timePercent = (elapsedSeconds * 100.0) / fiveHoursInSeconds;

                // Return usage-to-time ratio (lower is better)
                return timePercent > 0 ? utilization / timePercent : utilization;
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
