package xyz.jphil.ccapis.usage_tracker;

import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.usage_tracker.api.UsageApiClient;
import xyz.jphil.ccapis.usage_tracker.config.Settings;
import xyz.jphil.ccapis.usage_tracker.config.SettingsManager;
import xyz.jphil.ccapis.usage_tracker.model.AccountUsage;

import java.util.ArrayList;

/**
 * Basic test for usage tracker functionality
 * Tests that credentials load correctly and usage can be fetched
 *
 * Run with: mvn exec:java -Dexec.mainClass="xyz.jphil.ccapis.usage_tracker.UsageTrackerBasicTest" -pl xyz-jphil-ccapis-usage_tracker
 */
public class UsageTrackerBasicTest {

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  Usage Tracker Basic Functionality Test");
        System.out.println("==============================================\n");

        try {
            // Test 1: Load UI Settings
            System.out.println("TEST 1: Loading UI Settings");
            System.out.println("----------------------------");
            var settingsManager = new SettingsManager();
            Settings settings;
            try {
                settings = settingsManager.load();
                System.out.println("✓ UI Settings loaded from: " + settingsManager.getSettingsPath());
                System.out.println("  - Layout: " + settings.getUiSettings().layout());
                System.out.println("  - Refresh interval: " + settings.getUiSettings().usageRefreshIntervalSecs() + "s");
                System.out.println("  - Ping interval: " + settings.getUiSettings().pingIntervalDurSec() + "s");
                System.out.println("  - Ping message visible: " + settings.getUiSettings().pingMessageVisible());
            } catch (Exception e) {
                System.err.println("✗ FAILED to load UI settings: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
                return;
            }

            // Test 2: Load Shared Credentials
            System.out.println("\nTEST 2: Loading Shared Credentials");
            System.out.println("-----------------------------------");
            var credentialsManager = new CredentialsManager();
            var credentials = credentialsManager.load();
            System.out.println("✓ Credentials loaded from: " + credentialsManager.getCredentialsPath());
            System.out.println("  Total credentials: " + credentials.credentials().size());

            var trackUsageCredentials = credentials.getTrackUsageCredentials();
            System.out.println("  Credentials with trackUsage=true: " + trackUsageCredentials.size());

            var pingCredentials = credentials.getPingEnabledCredentials();
            System.out.println("  Credentials with ping=true: " + pingCredentials.size());

            if (trackUsageCredentials.isEmpty()) {
                System.err.println("✗ WARNING: No credentials with trackUsage=true found!");
                System.exit(1);
                return;
            }

            // Test 3: Create API Client
            System.out.println("\nTEST 3: Creating API Client");
            System.out.println("----------------------------");
            var apiClient = new UsageApiClient();
            System.out.println("✓ API Client created");

            // Test 4: Fetch Usage for Each Credential
            System.out.println("\nTEST 4: Fetching Usage Data");
            System.out.println("----------------------------");
            var accountUsages = new ArrayList<AccountUsage>();
            int successCount = 0;
            int failCount = 0;

            for (var credential : trackUsageCredentials) {
                System.out.println("\nFetching usage for: " + credential.id() + " (" + credential.name() + ")");
                System.out.println("  Email: " + credential.email());
                System.out.println("  Base URL: " + credential.resolvedCcapiBaseUrl());
                System.out.println("  Tier: " + credential.tier());
                System.out.println("  Ping enabled: " + credential.ping());

                try {
                    var usageData = apiClient.fetchUsageWithRetry(credential, 3);
                    var accountUsage = new AccountUsage(credential, usageData);
                    accountUsages.add(accountUsage);

                    System.out.println("  ✓ Usage fetched successfully");

                    // Display usage info
                    if (usageData != null && usageData.fiveHour() != null) {
                        var usage = usageData.fiveHour();
                        System.out.println("    - Utilization: " + usage.utilization() + "%");
                        System.out.println("    - Resets at: " + usage.resetsAt());
                    } else {
                        System.out.println("    - No usage data available");
                    }

                    successCount++;
                } catch (Exception e) {
                    System.err.println("  ✗ FAILED: " + e.getMessage());
                    failCount++;
                }
            }

            // Summary
            System.out.println("\n==============================================");
            System.out.println("  Test Summary");
            System.out.println("==============================================");
            System.out.println("Total credentials tested: " + trackUsageCredentials.size());
            System.out.println("Successful: " + successCount);
            System.out.println("Failed: " + failCount);

            if (failCount > 0) {
                System.err.println("\n⚠ Some tests failed!");
                System.exit(1);
            } else {
                System.out.println("\n✓ All tests passed!");
            }

            // Cleanup
            apiClient.shutdown();

        } catch (Exception e) {
            System.err.println("\n✗ TEST FAILED WITH EXCEPTION:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
