package xyz.jphil.ccapis.ping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jphil.ccapis.CCAPIClient;
import xyz.jphil.ccapis.listener.UsageListener;
import xyz.jphil.ccapis.listener.UsageUpdateEvent;
import xyz.jphil.ccapis.model.CCAPICredential;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ping service that sends periodic messages to CCAPI to prevent 5-hour usage gaps.
 * Implements UsageListener to receive usage updates from usage_tracker.
 *
 * Ping Triggers:
 * 1. When usage is zero or null (immediate ping needed)
 * 2. After pingIntervalDurSec has elapsed since last ping (default: 3600s / 1 hour)
 */
public class PingService implements UsageListener {

    private static final Logger logger = LoggerFactory.getLogger(PingService.class);

    private final CCAPIClient client;
    private final int pingIntervalDurSec;
    private final boolean pingMessageVisible;

    // Track last ping time per credential ID
    private final Map<String, Long> lastPingTimes = new ConcurrentHashMap<>();

    /**
     * Create ping service
     *
     * @param pingIntervalDurSec interval between pings in seconds (default: 3600)
     * @param pingMessageVisible whether ping messages should be visible (default: true)
     */
    public PingService(int pingIntervalDurSec, boolean pingMessageVisible) {
        this.client = new CCAPIClient();
        this.pingIntervalDurSec = pingIntervalDurSec;
        this.pingMessageVisible = pingMessageVisible;
        logger.info("PingService initialized: interval={}s, messageVisible={}", pingIntervalDurSec, pingMessageVisible);
    }

    /**
     * Create ping service with defaults (1 hour interval, visible messages)
     */
    public PingService() {
        this(3600, true);
    }

    @Override
    public void onUsageUpdate(UsageUpdateEvent event) {
        var credential = event.credential();

        // Only ping if enabled for this credential
        if (!credential.ping()) {
            logger.debug("Ping disabled for credential: {}", credential.id());
            return;
        }

        boolean shouldPing = false;
        String reason = null;

        // Check if usage is zero or null (always ping immediately)
        if (event.isUsageZeroOrNull()) {
            shouldPing = true;
            reason = "usage is zero/null";
        }
        // Usage is non-zero - check if ping interval has elapsed
        else {
            var lastPingTime = lastPingTimes.get(credential.id());
            var now = System.currentTimeMillis();

            if (lastPingTime == null) {
                // Never pinged before, but usage is non-zero
                // Initialize lastPingTime to now, wait full interval before first ping
                lastPingTimes.put(credential.id(), now);
                logger.debug("First check for {}, usage non-zero, waiting {} seconds before first ping",
                            credential.id(), pingIntervalDurSec);
                shouldPing = false;
            } else {
                var elapsedSeconds = (now - lastPingTime) / 1000;
                if (elapsedSeconds >= pingIntervalDurSec) {
                    shouldPing = true;
                    reason = String.format("interval elapsed (%ds)", elapsedSeconds);
                }
            }
        }

        if (shouldPing) {
            executePing(credential, reason);
        } else {
            logger.debug("No ping needed for credential: {}", credential.id());
        }
    }

    /**
     * Execute ping for a credential
     */
    private void executePing(CCAPICredential credential, String reason) {
        try {
            logger.info("Pinging credential {} ({}): {}", credential.id(), credential.name(), reason);

            // Generate ping message
            var message = PingMessageGenerator.generatePingMessage();

            // Create conversation (temporary = !pingMessageVisible)
            var isTemporary = !pingMessageVisible;
            var chatName = "Hi " + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm"));

            var conversation = client.createChat(credential, chatName, isTemporary);
            logger.debug("Created ping conversation: {} (temporary={})", conversation.uuid(), isTemporary);

            // Send ping message
            var response = client.sendMessageStreaming(
                credential,
                conversation.uuid(),
                message,
                null  // No streaming callback needed
            );

            logger.info("Ping successful for {}: sent='{}', response_length={}",
                credential.id(), message, response.length());

            // Update last ping time
            lastPingTimes.put(credential.id(), System.currentTimeMillis());

        } catch (Exception e) {
            logger.error("Failed to ping credential {}: {}", credential.id(), e.getMessage(), e);
        }
    }

    /**
     * Manually trigger ping for a credential (useful for testing)
     */
    public void manualPing(CCAPICredential credential) {
        executePing(credential, "manual trigger");
    }

    /**
     * Get last ping time for a credential
     */
    public Long getLastPingTime(String credentialId) {
        return lastPingTimes.get(credentialId);
    }

    /**
     * Shutdown the client
     */
    public void shutdown() {
        client.shutdown();
    }
}
