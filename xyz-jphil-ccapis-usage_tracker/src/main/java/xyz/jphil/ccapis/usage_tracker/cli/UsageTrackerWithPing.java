package xyz.jphil.ccapis.usage_tracker.cli;

import xyz.jphil.ccapis.ping.PingService;
import xyz.jphil.ccapis.usage_tracker.config.SettingsManager;

/**
 * Main class for running usage tracker with ping service integration.
 * This is the entry point for PRP-04 (ping feature).
 */
public class UsageTrackerWithPing {

    public static void main(String[] args) {
        var cli = new UsageTrackerCLI();

        try {
            // Load UI settings to get ping configuration
            var settingsManager = new SettingsManager();
            var settings = settingsManager.load();
            var ui = settings.getUiSettings();

            // Create and register ping service
            var pingService = new PingService(
                    ui.pingIntervalDurSec(),      // From CCAPIsUsageTrackerSettings.xml
                    ui.pingMessageVisible()        // From CCAPIsUsageTrackerSettings.xml
            );

            cli.addUsageListener(pingService);

            System.out.println("=".repeat(60));
            System.out.println("CCAPI Usage Tracker with Ping Service");
            System.out.println("Ping interval: " + ui.pingIntervalDurSec() + "s");
            System.out.println("Ping messages visible: " + ui.pingMessageVisible());
            System.out.println("=".repeat(60));
            System.out.println();

            // Run the usage tracker (ping service will automatically trigger)
            cli.run();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
