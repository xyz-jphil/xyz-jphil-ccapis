package xyz.jphil.ccapis.ping;

import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.listener.UsageUpdateEvent;

/**
 * Standalone test for ping service.
 * Tests the ping functionality independently of usage_tracker.
 *
 * To run: mvn exec:java -Dexec.mainClass="xyz.jphil.ccapis.ping.PingServiceTest"
 */
public class PingServiceTest {

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Ping Service Standalone Test");
        System.out.println("=".repeat(60));

        try {
            // Load credentials
            var credentialsManager = new CredentialsManager();
            var credentials = credentialsManager.load();

            // Get first ping-enabled credential
            var pingCredentials = credentials.getPingEnabledCredentials();
            if (pingCredentials.isEmpty()) {
                System.out.println("[ERROR] No credentials with ping=true found");
                System.out.println("Please configure CCAPIsCredentials.xml at: " + credentialsManager.getCredentialsPath());
                System.exit(1);
                return;
            }

            var credential = pingCredentials.get(0);
            System.out.println("Testing ping for credential: " + credential.id() + " (" + credential.name() + ")");
            System.out.println("Base URL: " + credential.resolvedCcapiBaseUrl());
            System.out.println();

            // Create ping service
            var pingService = new PingService(
                    10,      // 10 second interval for testing
                    false    // Hidden messages for testing
            );

            // Test manual ping
            System.out.println("[TEST 1] Manual ping trigger...");
            pingService.manualPing(credential);
            System.out.println("[TEST 1] Manual ping completed");
            System.out.println();

            // Test ping via usage event (with null usage to simulate zero usage)
            System.out.println("[TEST 2] Ping via usage event (null usage)...");
            var event = new UsageUpdateEvent()
                    .credential(credential)
                    .usageData(null)  // Null simulates zero usage
                    .timestamp(System.currentTimeMillis());
            pingService.onUsageUpdate(event);
            System.out.println("[TEST 2] Event-based ping completed");
            System.out.println();

            // Check last ping time
            var lastPingTime = pingService.getLastPingTime(credential.id());
            if (lastPingTime != null) {
                var now = System.currentTimeMillis();
                var elapsed = (now - lastPingTime) / 1000;
                System.out.println("Last ping was " + elapsed + " seconds ago");
            }

            // Cleanup
            pingService.shutdown();

            System.out.println();
            System.out.println("=".repeat(60));
            System.out.println("All tests completed successfully!");
            System.out.println("=".repeat(60));

        } catch (Exception e) {
            System.err.println("[ERROR] Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
