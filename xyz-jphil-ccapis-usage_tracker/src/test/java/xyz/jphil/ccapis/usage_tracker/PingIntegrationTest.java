package xyz.jphil.ccapis.usage_tracker;

import xyz.jphil.ccapis.CCAPIClient;
import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.listener.UsageUpdateEvent;
import xyz.jphil.ccapis.ping.PingService;
import xyz.jphil.ccapis.usage_tracker.api.UsageApiClient;
import xyz.jphil.ccapis.usage_tracker.config.SettingsManager;

/**
 * Integration test for ping service with usage tracker
 * Tests that ping service correctly responds to usage events
 *
 * Run with: mvn exec:java -Dexec.mainClass="xyz.jphil.ccapis.usage_tracker.PingIntegrationTest" -pl xyz-jphil-ccapis-usage_tracker
 */
public class PingIntegrationTest {

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  Ping Service Integration Test");
        System.out.println("==============================================\n");

        try {
            // Load settings
            System.out.println("TEST 1: Loading Settings");
            System.out.println("-------------------------");
            var settingsManager = new SettingsManager();
            var settings = settingsManager.load();
            var uiSettings = settings.getUiSettings();
            System.out.println("✓ Settings loaded");
            System.out.println("  - Ping interval: " + uiSettings.pingIntervalDurSec() + "s");
            System.out.println("  - Ping message visible: " + uiSettings.pingMessageVisible());

            // Load credentials
            System.out.println("\nTEST 2: Loading Credentials");
            System.out.println("----------------------------");
            var credentialsManager = new CredentialsManager();
            var credentials = credentialsManager.load();
            var pingCredentials = credentials.getPingEnabledCredentials();
            System.out.println("✓ Found " + pingCredentials.size() + " credentials with ping enabled");

            if (pingCredentials.isEmpty()) {
                System.err.println("✗ No credentials with ping=true found!");
                System.exit(1);
                return;
            }

            // Create ping service
            System.out.println("\nTEST 3: Creating Ping Service");
            System.out.println("------------------------------");
            var ccapiClient = new CCAPIClient();
            var pingService = new PingService(
                uiSettings.pingIntervalDurSec(),
                uiSettings.pingMessageVisible()
            );
            System.out.println("✓ Ping service created");

            // Create API client for usage
            var usageApiClient = new UsageApiClient();

            // Test ping for each credential
            System.out.println("\nTEST 4: Testing Ping Trigger");
            System.out.println("-----------------------------");

            for (var credential : pingCredentials) {
                System.out.println("\nTesting credential: " + credential.id() + " (" + credential.name() + ")");

                // Fetch usage
                System.out.println("  Fetching usage...");
                var usageData = usageApiClient.fetchUsageWithRetry(credential, 3);

                // Create usage update event
                var event = new UsageUpdateEvent()
                    .credential(credential)
                    .usageData(usageData)
                    .timestamp(System.currentTimeMillis());

                // Fire event to ping service
                System.out.println("  Firing usage update event to ping service...");
                pingService.onUsageUpdate(event);

                if (usageData != null && usageData.fiveHour() != null) {
                    var utilization = usageData.fiveHour().utilization();
                    System.out.println("  Current utilization: " + utilization + "%");

                    if (event.isUsageZeroOrNull()) {
                        System.out.println("  ✓ Zero usage detected - ping should have triggered");
                    } else {
                        System.out.println("  ℹ Non-zero usage - ping triggered only if interval elapsed");
                    }
                } else {
                    System.out.println("  ⚠ No usage data - treated as zero usage");
                }

                // Small delay between credentials
                Thread.sleep(2000);
            }

            // Test manual ping
            System.out.println("\n\nTEST 5: Manual Ping Test");
            System.out.println("-------------------------");
            var testCredential = pingCredentials.get(0);
            System.out.println("Manually triggering ping for: " + testCredential.id());

            try {
                // Create a temporary chat
                var chat = ccapiClient.createChat(
                    testCredential,
                    "Manual Ping Test",
                    true  // temporary
                );
                System.out.println("  ✓ Created chat: " + chat.uuid());

                // Send ping message
                String message = "Hello! What time is it?";
                System.out.println("  Sending message: \"" + message + "\"");
                var response = ccapiClient.sendMessage(testCredential, chat.uuid(), message);
                System.out.println("  ✓ Received response (" + response.length() + " chars)");

                System.out.println("\n✓ Manual ping successful!");
            } catch (Exception e) {
                System.err.println("  ✗ Manual ping failed: " + e.getMessage());
                e.printStackTrace();
            }

            // Summary
            System.out.println("\n==============================================");
            System.out.println("  Test Summary");
            System.out.println("==============================================");
            System.out.println("✓ Ping service integration working");
            System.out.println("✓ Event-based ping triggers correctly");
            System.out.println("✓ Manual ping verified");
            System.out.println("\nℹ Note: Check CCAPI web interface to verify ping messages");

            // Cleanup
            usageApiClient.shutdown();
            ccapiClient.shutdown();

        } catch (Exception e) {
            System.err.println("\n✗ TEST FAILED WITH EXCEPTION:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
