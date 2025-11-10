package xyz.jphil.ccapis.health;

import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.model.CCAPIsCredentials;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Watches the CCAPIsCredentials.xml file for changes and triggers reload callbacks.
 * Runs in a background thread and can be stopped gracefully.
 */
public class CredentialsFileWatcher {

    private final Path credentialsPath;
    private final CredentialsManager credentialsManager;
    private final Consumer<CCAPIsCredentials> onReloadCallback;
    private final AccountRotationLogger logger;

    private Thread watcherThread;
    private volatile boolean running;
    private WatchService watchService;

    /**
     * Create file watcher
     * @param credentialsManager Manager to load credentials
     * @param onReloadCallback Callback invoked when file changes (called with new credentials)
     * @param logger Logger for rotation events
     */
    public CredentialsFileWatcher(CredentialsManager credentialsManager,
                                 Consumer<CCAPIsCredentials> onReloadCallback,
                                 AccountRotationLogger logger) {
        this.credentialsManager = credentialsManager;
        this.credentialsPath = credentialsManager.getCredentialsPath();
        this.onReloadCallback = onReloadCallback;
        this.logger = logger;
    }

    /**
     * Start watching the credentials file for changes
     */
    public void start() {
        if (running) {
            return; // Already running
        }

        running = true;
        watcherThread = new Thread(this::watchLoop, "CredentialsFileWatcher");
        watcherThread.setDaemon(true); // Don't prevent JVM shutdown
        watcherThread.start();
    }

    /**
     * Stop watching
     */
    public void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
    }

    /**
     * Main watch loop (runs in background thread)
     */
    private void watchLoop() {
        try {
            // Get parent directory of credentials file
            var watchDir = credentialsPath.getParent();
            if (watchDir == null) {
                System.err.println("[WATCHER] Cannot watch credentials file: no parent directory");
                return;
            }

            // Create watch service
            watchService = FileSystems.getDefault().newWatchService();

            // Register directory for modifications
            watchDir.register(watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE);

            while (running) {
                WatchKey key;
                try {
                    // Wait for events (blocks)
                    key = watchService.take();
                } catch (InterruptedException e) {
                    // Stop requested
                    break;
                } catch (java.nio.file.ClosedWatchServiceException e) {
                    // Watch service closed, exit gracefully
                    break;
                }

                // Process events
                for (var event : key.pollEvents()) {
                    var kind = event.kind();

                    // Skip overflow events
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    // Get the filename that changed
                    @SuppressWarnings("unchecked")
                    var changedPath = ((WatchEvent<Path>) event).context();
                    var fullPath = watchDir.resolve(changedPath);

                    // Check if it's our credentials file
                    if (Files.isSameFile(fullPath, credentialsPath)) {
                        handleCredentialsFileChanged();
                    }
                }

                // Reset key to receive further events
                if (!key.reset()) {
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("[WATCHER] Error watching credentials file: " + e.getMessage());
        } finally {
            running = false;
        }
    }

    /**
     * Handle credentials file change
     */
    private void handleCredentialsFileChanged() {
        logger.logCredentialsFileChanged();

        // Small delay to ensure file write is complete
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            return;
        }

        // Reload credentials
        try {
            var newCredentials = credentialsManager.load();
            var activeCount = newCredentials.getActiveCredentials().size();
            var totalCount = newCredentials.credentials().size();

            logger.logCredentialsReloaded(activeCount, totalCount);

            // Invoke callback with new credentials
            if (onReloadCallback != null) {
                onReloadCallback.accept(newCredentials);
            }

        } catch (Exception e) {
            System.err.println("[WATCHER] Error reloading credentials: " + e.getMessage());
        }
    }

    /**
     * Check if watcher is running
     */
    public boolean isRunning() {
        return running;
    }
}
