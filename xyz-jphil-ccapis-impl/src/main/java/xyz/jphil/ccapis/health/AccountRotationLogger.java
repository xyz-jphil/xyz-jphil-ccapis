package xyz.jphil.ccapis.health;

import xyz.jphil.ccapis.model.CCAPICredential;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles logging for account rotation and selection.
 * Supports both normal and fine (verbose) logging modes.
 */
public class AccountRotationLogger {

    private final boolean fineMode;
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private final int summaryInterval;

    /**
     * Create logger with mode detection from system properties
     */
    public AccountRotationLogger() {
        this(isFineModeEnabled(), 10);
    }

    /**
     * Create logger with explicit mode
     * @param fineMode true for verbose logging
     * @param summaryInterval log summary every N requests
     */
    public AccountRotationLogger(boolean fineMode, int summaryInterval) {
        this.fineMode = fineMode;
        this.summaryInterval = summaryInterval;
    }

    /**
     * Check if fine/verbose mode is enabled via system property
     */
    private static boolean isFineModeEnabled() {
        return "true".equalsIgnoreCase(System.getProperty("xyz.jphil.ccapis.verbose")) ||
               "true".equalsIgnoreCase(System.getProperty("xyz.jphil.ccapis.fine")) ||
               "FINE".equalsIgnoreCase(System.getProperty("xyz.jphil.ccapis.logLevel"));
    }

    /**
     * Log account selection for a request
     * @param selected The selected credential
     * @param health Health status of the selected account
     */
    public void logAccountSelection(CCAPICredential selected, AccountHealth health) {
        if (selected == null) {
            System.err.println("[ROTATION] ✗ No available accounts!");
            return;
        }

        var requestNum = requestCounter.incrementAndGet();
        var usagePercent = health != null ? getUsagePercent(health) : 0.0;

        if (fineMode) {
            // Verbose mode: detailed info
            var tier = selected.tier() == 0 ? "F" : String.valueOf(selected.tier());
            System.out.printf("[ROTATION] [%s] tier=%s usage=%.1f%% → Request #%d%n",
                selected.id(), tier, usagePercent, requestNum);
        } else {
            // Normal mode: compact info
            System.out.printf("[%s] %.0f%% → Req#%d%n",
                selected.id(), usagePercent, requestNum);
        }
    }

    /**
     * Log account load summary
     * @param credentials All credentials
     * @param healthMonitor Health monitor with usage data
     */
    public void logLoadSummary(List<CCAPICredential> credentials, AccountHealthMonitor healthMonitor) {
        var requestNum = requestCounter.get();

        // Log summary at intervals or in fine mode
        if (requestNum % summaryInterval == 0 || fineMode) {
            System.out.println("─".repeat(60));
            System.out.println("Account Load Summary (after " + requestNum + " requests):");

            for (var cred : credentials) {
                if (!cred.active()) {
                    continue; // Skip inactive accounts
                }

                var health = healthMonitor.getHealth(cred.id());
                if (health == null) {
                    System.out.printf("  [%s] No data yet%n", cred.id());
                    continue;
                }

                var status = health.isAvailable() ? "✓" : "✗";
                var usagePercent = getUsagePercent(health);
                var successRate = 0.0; // Success rate not tracked in base AccountHealth
                var tier = cred.tier() == 0 ? "F" : "T" + cred.tier();

                if (fineMode) {
                    // Verbose: show all details
                    System.out.printf("  %s [%s] %s: usage=%.1f%% failures=%d%n",
                        status, cred.id(), tier,
                        usagePercent, health.consecutiveFailures());
                } else {
                    // Compact: just essential info
                    System.out.printf("  %s [%s] %s: %.0f%%%n",
                        status, cred.id(), tier, usagePercent);
                }
            }
            System.out.println("─".repeat(60));
        }
    }

    /**
     * Log credentials reload event
     */
    public void logCredentialsReloaded(int activeCount, int totalCount) {
        System.out.printf("[ROTATION] Credentials reloaded: %d active (of %d total)%n",
            activeCount, totalCount);
    }

    /**
     * Log credentials file change detected
     */
    public void logCredentialsFileChanged() {
        if (fineMode) {
            System.out.println("[ROTATION] CCAPIsCredentials.xml changed, reloading...");
        }
    }

    /**
     * Log account becoming unavailable
     */
    public void logAccountUnavailable(String accountId, String reason) {
        System.out.printf("[ROTATION] Account [%s] now unavailable: %s%n", accountId, reason);
    }

    /**
     * Log when all candidates are considered (fine mode only)
     */
    public void logCandidatesConsidered(List<CCAPICredential> candidates, AccountHealthMonitor healthMonitor) {
        if (!fineMode || candidates.isEmpty()) {
            return;
        }

        System.out.println("  Available candidates:");
        for (var cred : candidates) {
            var health = healthMonitor.getHealth(cred.id());
            var usagePercent = health != null ? getUsagePercent(health) : 0.0;
            System.out.printf("    - [%s] usage=%.1f%%%n", cred.id(), usagePercent);
        }
    }

    /**
     * Get current request counter value
     */
    public int getRequestCount() {
        return requestCounter.get();
    }

    /**
     * Helper to get usage percent from base module AccountHealth
     */
    private double getUsagePercent(AccountHealth health) {
        if (health.latestUsage() != null && health.latestUsage().fiveHour() != null) {
            return health.latestUsage().fiveHour().utilization();
        }
        return 0.0;
    }

    /**
     * Reset request counter
     */
    public void resetCounter() {
        requestCounter.set(0);
    }
}
