package xyz.jphil.ccapis;

import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.health.*;
import xyz.jphil.ccapis.model.CCAPICredential;
import xyz.jphil.ccapis.model.CCAPIsCredentials;
import xyz.jphil.ccapis.model.CircuitBreakerConfig;
import xyz.jphil.ccapis.model.UsageData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Enhanced CCAPI client with circuit breaker health checking and smart account rotation.
 * Automatically:
 * - Monitors account health and failures
 * - Skips accounts with quota exhaustion
 * - Applies cooldown to failing accounts
 * - Selects best available account based on usage and health
 * - Rotates accounts to distribute load uniformly
 * - Watches credentials file for dynamic updates
 * - Logs account selection and load distribution
 *
 * Usage:
 * 1. Create with circuit breaker config and credentials manager
 * 2. Client automatically refreshes usage and selects best account
 * 3. Use executeWithAutoRotation() for automatic account selection
 * 4. Call startCredentialsFileWatcher() to monitor credentials file changes
 */
public class CCAPIClientWithHealthCheck {

    private final CCAPIClient client;
    private final AccountHealthMonitor healthMonitor;
    private final UsageBasedAccountSelector accountSelector;
    private final AccountRotationLogger logger;
    private final CredentialsManager credentialsManager;
    private CredentialsFileWatcher fileWatcher;

    // Current credentials (updated dynamically)
    private volatile List<CCAPICredential> currentCredentials;

    /**
     * Create client with circuit breaker configuration.
     * Uses default credentials manager.
     */
    public CCAPIClientWithHealthCheck(CircuitBreakerConfig config) {
        this(config, null, new CredentialsManager());
    }

    /**
     * Create client with circuit breaker and request/response file logger.
     * Uses default credentials manager.
     */
    public CCAPIClientWithHealthCheck(CircuitBreakerConfig config,
                                     xyz.jphil.ccapis.proxy.RequestResponseFileLogger fileLogger) {
        this(config, fileLogger, new CredentialsManager());
    }

    /**
     * Create client with circuit breaker, file logger, and custom credentials manager.
     */
    public CCAPIClientWithHealthCheck(CircuitBreakerConfig config,
                                     xyz.jphil.ccapis.proxy.RequestResponseFileLogger fileLogger,
                                     CredentialsManager credentialsManager) {
        this.client = fileLogger != null ? new CCAPIClient(fileLogger) : new CCAPIClient();
        this.healthMonitor = new AccountHealthMonitor(config);
        this.logger = new AccountRotationLogger();
        this.accountSelector = new UsageBasedAccountSelector(client, healthMonitor, logger);
        this.credentialsManager = credentialsManager;
        this.currentCredentials = new ArrayList<>();

        // Load initial credentials
        loadCredentials();
    }

    /**
     * Load credentials from manager
     */
    private void loadCredentials() {
        try {
            var credentials = credentialsManager.load();
            this.currentCredentials = new ArrayList<>(credentials.getActiveCredentials());
            logger.logCredentialsReloaded(currentCredentials.size(), credentials.credentials().size());
        } catch (Exception e) {
            System.err.println("[ROTATION] Failed to load credentials: " + e.getMessage());
            this.currentCredentials = new ArrayList<>();
        }
    }

    /**
     * Handle credentials reload (called by file watcher)
     */
    private void handleCredentialsReload(CCAPIsCredentials newCredentials) {
        var oldCredentials = this.currentCredentials;
        this.currentCredentials = new ArrayList<>(newCredentials.getActiveCredentials());

        // Clear health for removed accounts
        var oldIds = oldCredentials.stream().map(CCAPICredential::id).toList();
        var newIds = currentCredentials.stream().map(CCAPICredential::id).toList();

        for (var oldId : oldIds) {
            if (!newIds.contains(oldId)) {
                healthMonitor.reset(oldId);
            }
        }
    }

    /**
     * Start watching credentials file for changes.
     * When file changes, credentials are automatically reloaded.
     */
    public void startCredentialsFileWatcher() {
        if (fileWatcher != null && fileWatcher.isRunning()) {
            return; // Already running
        }

        fileWatcher = new CredentialsFileWatcher(
            credentialsManager,
            this::handleCredentialsReload,
            logger
        );
        fileWatcher.start();
    }

    /**
     * Stop watching credentials file
     */
    public void stopCredentialsFileWatcher() {
        if (fileWatcher != null) {
            fileWatcher.stop();
            fileWatcher = null;
        }
    }

    /**
     * Execute operation with automatic account selection and rotation.
     * Selects best available account based on usage and health.
     *
     * @param operation Operation to execute with selected credential
     * @param <T> Return type
     * @return Operation result
     * @throws IOException if no accounts available or operation fails
     */
    public <T> T executeWithAutoRotation(Function<CCAPICredential, T> operation) throws IOException {
        // Select best account
        var credential = accountSelector.selectBestAccount(currentCredentials);

        if (credential == null) {
            throw new IOException("No available accounts for request");
        }

        // Execute operation with health tracking
        return executeWithHealthCheck(credential, c -> operation.apply(credential));
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
     * Refresh usage for current credentials
     */
    public void refreshUsage() {
        refreshUsage(currentCredentials);
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
     * Get current active credentials
     */
    public List<CCAPICredential> getCurrentCredentials() {
        return new ArrayList<>(currentCredentials);
    }

    /**
     * Get account rotation logger
     */
    public AccountRotationLogger getLogger() {
        return logger;
    }

    /**
     * Get account selector
     */
    public UsageBasedAccountSelector getAccountSelector() {
        return accountSelector;
    }

    /**
     * Shutdown the client and stop file watcher.
     */
    public void shutdown() {
        stopCredentialsFileWatcher();
        client.shutdown();
    }
}
