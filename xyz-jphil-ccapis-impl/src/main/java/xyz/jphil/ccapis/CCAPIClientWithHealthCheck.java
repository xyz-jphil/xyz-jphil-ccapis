package xyz.jphil.ccapis;

import xyz.jphil.ccapis.health.*;
import xyz.jphil.ccapis.model.CCAPICredential;
import xyz.jphil.ccapis.model.CircuitBreakerConfig;
import xyz.jphil.ccapis.model.UsageData;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * Enhanced CCAPI client with circuit breaker health checking.
 * Automatically:
 * - Monitors account health and failures
 * - Skips accounts with quota exhaustion
 * - Applies cooldown to failing accounts
 * - Selects best available account based on usage and health
 *
 * Usage:
 * 1. Create with circuit breaker config
 * 2. Call refreshUsage() to update account health
 * 3. Use executeWithHealthCheck() for automatic account selection and failure tracking
 */
public class CCAPIClientWithHealthCheck {

    private final CCAPIClient client;
    private final AccountHealthMonitor healthMonitor;

    /**
     * Create client with circuit breaker configuration.
     */
    public CCAPIClientWithHealthCheck(CircuitBreakerConfig config) {
        this.client = new CCAPIClient();
        this.healthMonitor = new AccountHealthMonitor(config);
    }

    /**
     * Create client with circuit breaker and request/response file logger.
     */
    public CCAPIClientWithHealthCheck(CircuitBreakerConfig config,
                                     xyz.jphil.ccapis.proxy.RequestResponseFileLogger fileLogger) {
        this.client = new CCAPIClient(fileLogger);
        this.healthMonitor = new AccountHealthMonitor(config);
    }

    /**
     * Refresh usage data for all accounts and update health status.
     * Call this before selecting accounts to ensure fresh quota information.
     */
    public void refreshUsage(List<CCAPICredential> credentials) {
        for (var credential : credentials) {
            try {
                var usage = client.fetchUsage(credential);
                healthMonitor.updateUsage(credential.id(), usage);
            } catch (IOException e) {
                // Failed to fetch usage - classify and record error
                var failureType = ErrorClassifier.classify(e);
                healthMonitor.recordFailure(credential.id(), failureType);
            }
        }
    }

    /**
     * Refresh usage for a single account if stale.
     */
    public void refreshUsageIfStale(CCAPICredential credential) {
        if (healthMonitor.isUsageStale(credential.id())) {
            try {
                var usage = client.fetchUsage(credential);
                healthMonitor.updateUsage(credential.id(), usage);
            } catch (IOException e) {
                var failureType = ErrorClassifier.classify(e);
                healthMonitor.recordFailure(credential.id(), failureType);
            }
        }
    }

    /**
     * Get best available account from a list.
     * Automatically filters out unavailable accounts and sorts by health/usage.
     * Returns null if no accounts available.
     */
    public CCAPICredential selectBestAccount(List<CCAPICredential> credentials) {
        // Refresh usage for accounts with stale data
        credentials.forEach(this::refreshUsageIfStale);

        return healthMonitor.selectBestAccount(credentials);
    }

    /**
     * Get all available accounts sorted by health and usage.
     */
    public List<CCAPICredential> getAvailableAccounts(List<CCAPICredential> credentials) {
        credentials.forEach(this::refreshUsageIfStale);
        return healthMonitor.getAvailableAccounts(credentials);
    }

    /**
     * Execute an operation with automatic health checking and failure tracking.
     * If operation fails, records failure and applies circuit breaker logic.
     * If operation succeeds, records success and clears failure state.
     *
     * @param credential the credential to use
     * @param operation the operation to execute
     * @param <T> return type
     * @return operation result
     * @throws IOException if operation fails
     */
    public <T> T executeWithHealthCheck(CCAPICredential credential,
                                        Function<CCAPIClient, T> operation) throws IOException {
        // Check if account is available
        if (!healthMonitor.isAccountAvailable(credential.id())) {
            var health = healthMonitor.getHealth(credential.id());
            throw new IOException(String.format(
                "Account %s is not available: %s",
                credential.id(), health.getStatusString()));
        }

        try {
            // Execute operation
            var result = operation.apply(client);

            // Record success
            healthMonitor.recordSuccess(credential.id());

            return result;
        } catch (RuntimeException e) {
            // Unwrap IOException if wrapped in RuntimeException
            if (e.getCause() instanceof IOException ioException) {
                var failureType = ErrorClassifier.classify(ioException);
                healthMonitor.recordFailure(credential.id(), failureType);
                throw ioException;
            }
            throw e;
        } catch (Exception e) {
            // Handle any other exceptions
            healthMonitor.recordFailure(credential.id(), FailureType.GENERIC_ERROR);
            throw new IOException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch usage with health tracking.
     */
    public UsageData fetchUsage(CCAPICredential credential) throws IOException {
        return executeWithHealthCheck(credential, c -> {
            try {
                return c.fetchUsage(credential);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Send message with health tracking.
     */
    public String sendMessage(CCAPICredential credential, String conversationId, String message)
            throws IOException {
        return executeWithHealthCheck(credential, c -> {
            try {
                return c.sendMessage(credential, conversationId, message);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Get the underlying CCAPIClient for direct access.
     */
    public CCAPIClient getClient() {
        return client;
    }

    /**
     * Get the health monitor for direct access.
     */
    public AccountHealthMonitor getHealthMonitor() {
        return healthMonitor;
    }

    /**
     * Get health summary for all tracked accounts.
     */
    public String getHealthSummary() {
        return healthMonitor.getHealthSummary();
    }

    /**
     * Check if an account is available.
     */
    public boolean isAccountAvailable(String accountId) {
        return healthMonitor.isAccountAvailable(accountId);
    }

    /**
     * Shutdown the client.
     */
    public void shutdown() {
        client.shutdown();
    }
}
