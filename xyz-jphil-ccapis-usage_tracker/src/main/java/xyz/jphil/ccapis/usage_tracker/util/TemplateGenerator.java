package xyz.jphil.ccapis.usage_tracker.util;

import xyz.jphil.ccapis.usage_tracker.config.SettingsManager;

/**
 * Utility to generate empty CCAPIsUsageTrackerSettings.xml template
 * Run this to create the initial configuration file
 */
public class TemplateGenerator {

    public static void main(String[] args) {
        try {
            var settingsManager = new SettingsManager();
            var settings = settingsManager.createEmptyTemplate();
            settingsManager.save(settings);

            System.out.println("Empty settings template created successfully!");
            System.out.println("Location: " + settingsManager.getSettingsPath());
            System.out.println("\nPlease edit the file and fill in your credentials:");
            System.out.println("  - ccapiBaseUrl: Your CCAPI base URL");
            System.out.println("  - credentials: Add your account details");
            System.out.println("    - id: Short alias for the account");
            System.out.println("    - name: Friendly name");
            System.out.println("    - email: Associated email");
            System.out.println("    - orgId: Organization ID");
            System.out.println("    - key: Session key");
            System.out.println("    - trackUsage: Set to 'true' to enable usage tracking");
            System.out.println("    - ping: Set to 'true' to enable ping checks (optional, default: false)");
            System.out.println("  - ui: UI settings (optional)");
            System.out.println("    - usageRefreshIntervalSecs: Usage refresh interval in seconds (default: 60)");
            System.out.println("    - pingIntervalDurSec: Ping interval duration in seconds (default: 3600)");
            System.out.println("    - lastScreenLoc: Last window position 'x,y' (auto-saved)");

        } catch (Exception e) {
            System.err.println("Failed to create template: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
