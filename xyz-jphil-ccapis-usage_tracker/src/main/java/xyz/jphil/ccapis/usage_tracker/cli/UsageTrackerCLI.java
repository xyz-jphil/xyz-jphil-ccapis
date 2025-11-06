package xyz.jphil.ccapis.usage_tracker.cli;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.listener.UsageListener;
import xyz.jphil.ccapis.listener.UsageUpdateEvent;
import xyz.jphil.ccapis.model.CCAPIsCredentials;
import xyz.jphil.ccapis.usage_tracker.api.UsageApiClient;
import xyz.jphil.ccapis.usage_tracker.config.Settings;
import xyz.jphil.ccapis.usage_tracker.config.SettingsManager;
import xyz.jphil.ccapis.usage_tracker.model.AccountUsage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line interface for usage tracker.
 * Displays usage status for all active accounts.
 * Now uses shared credentials from CCAPIsCredentials.xml.
 */
public class UsageTrackerCLI {

    private final SettingsManager settingsManager;
    private final CredentialsManager credentialsManager;
    private final List<UsageListener> listeners = new ArrayList<>();
    private Terminal terminal;
    private UsageApiClient apiClient;

    public UsageTrackerCLI() {
        this.settingsManager = new SettingsManager();
        this.credentialsManager = new CredentialsManager();
    }

    /**
     * Register a listener to receive usage update events
     */
    public void addUsageListener(UsageListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener
     */
    public void removeUsageListener(UsageListener listener) {
        listeners.remove(listener);
    }

    /**
     * Fire usage update event to all listeners
     */
    private void fireUsageUpdateEvent(UsageUpdateEvent event) {
        for (var listener : listeners) {
            try {
                listener.onUsageUpdate(event);
            } catch (Exception e) {
                System.err.println("Error in usage listener: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        var cli = new UsageTrackerCLI();
        try {
            cli.run();
        } catch (Exception e) {
            System.err.println("Error running CLI: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void run() throws Exception {
        // Initialize terminal
        terminal = TerminalBuilder.builder()
                .system(true)
                .dumb(true)
                .build();

        try {
            // Load UI settings
            Settings settings;
            try {
                settings = settingsManager.load();
            } catch (Exception e) {
                printError("Failed to load UI settings from: " + settingsManager.getSettingsPath());
                printError("Error: " + e.getMessage());
                printInfo("Please configure settings at: " + settingsManager.getSettingsPath());
                return;
            }

            // Load shared credentials
            CCAPIsCredentials credentials;
            try {
                credentials = credentialsManager.load();
            } catch (Exception e) {
                printError("Failed to load credentials from: " + credentialsManager.getCredentialsPath());
                printError("Error: " + e.getMessage());
                printInfo("Please configure credentials at: " + credentialsManager.getCredentialsPath());
                return;
            }

            // Get credentials with usage tracking enabled
            var activeCredentials = credentials.getTrackUsageCredentials();
            if (activeCredentials.isEmpty()) {
                printWarning("No credentials with trackUsage=true found");
                printInfo("Credentials location: " + credentialsManager.getCredentialsPath());
                return;
            }

            // Create API client (stateless - baseUrl is per-credential)
            apiClient = new UsageApiClient();

            // Print header
            printHeader();

            // Fetch usage for all active accounts (silently)
            var accountUsages = new ArrayList<AccountUsage>();
            for (var credential : activeCredentials) {
                try {
                    var usageData = apiClient.fetchUsageWithRetry(credential, 3);
                    var accountUsage = new AccountUsage(credential, usageData);
                    accountUsages.add(accountUsage);

                    // Fire usage update event to listeners (e.g., ping service)
                    var event = new UsageUpdateEvent()
                            .credential(credential)
                            .usageData(usageData)
                            .timestamp(System.currentTimeMillis());
                    fireUsageUpdateEvent(event);

                } catch (Exception e) {
                    printError("Failed to fetch usage for account: " + credential.id());
                    printError("Error: " + e.getMessage());

                    // Fire event even on failure (with null usageData)
                    var event = new UsageUpdateEvent()
                            .credential(credential)
                            .usageData(null)
                            .timestamp(System.currentTimeMillis());
                    fireUsageUpdateEvent(event);
                }
            }

            // Display all accounts
            for (var accountUsage : accountUsages) {
                printAccountUsage(accountUsage);
            }

            // Print summary
            printSummary(accountUsages);

            // Flush output
            terminal.flush();

        } finally {
            // Shutdown API client to kill OkHttp threads
            if (apiClient != null) {
                try {
                    apiClient.shutdown();
                } catch (Exception e) {
                    // Ignore shutdown errors
                }
            }

            // Close terminal
            if (terminal != null) {
                try {
                    terminal.close();
                } catch (Exception e) {
                    // Ignore close errors
                }
            }

            // Force immediate exit
            System.exit(0);
        }
    }

    private void printHeader() {
        var header = new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.CYAN))
                .append("CCAPI Usage Tracker")
                .append("\n")
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append("=".repeat(60))
                .append("\n")
                .toAnsi();

        terminal.writer().println(header);
        terminal.flush();
    }

    private void printAccountUsage(AccountUsage accountUsage) {
        // Account header with name and tier
        var tier = accountUsage.credential().tier();
        var tierDisplay = tier == 0 ? "F" : String.valueOf(tier);

        var header = new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW))
                .append(accountUsage.credential().id())
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append(" [" + tierDisplay + "] - ")
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE))
                .append(accountUsage.credential().name())
                .toAnsi();
        terminal.writer().println(header);

        // 5-Hour Window - compact single line
        var fiveHr = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE))
                .append("  5hr: ");

        var usagePercent = accountUsage.getFiveHourUsagePercent();
        var usageColor = getColorForUsage(usagePercent);
        fiveHr.style(AttributedStyle.BOLD.foreground(usageColor))
                .append(String.format("U:%3.0f%%", usagePercent));

        fiveHr.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append(String.format(" T:%3.0f%%", accountUsage.getFiveHourTimeElapsedPercent()));

        var ratio = accountUsage.getUsageToTimeRatio() * 100.0;
        var ratioColor = getColorForRatio(accountUsage.getUsageToTimeRatio());
        fiveHr.style(AttributedStyle.BOLD.foreground(ratioColor))
                .append(String.format(" R:%3.0f%%", ratio));

        fiveHr.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE))
                .append(" -> " + accountUsage.getTimeUntilReset() + " (resets " + accountUsage.getCycleResetTime() + ")");

        terminal.writer().println(fiveHr.toAnsi());

        // 7-Day windows if available
        var usageData = accountUsage.usageData();
        if (usageData != null && usageData.sevenDay() != null) {
            var sevenDay = usageData.sevenDay();
            var timeElapsed = calculateTimeElapsedPercent(sevenDay.resetsAt(), 7 * 24 * 60 * 60);
            var utilizationRate = timeElapsed > 0 ? (sevenDay.utilization() / timeElapsed) * 100.0 : 0.0;

            var sevenDayLine = new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE))
                    .append("  7day: ");

            var weeklyUsageColor = getColorForUsage(sevenDay.utilization());
            sevenDayLine.style(AttributedStyle.DEFAULT.foreground(weeklyUsageColor))
                    .append(String.format("U:%3.0f%%", (float)sevenDay.utilization()));

            sevenDayLine.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                    .append(String.format(" T:%3.0f%%", timeElapsed));

            var weeklyRatioColor = utilizationRate >= 120 ? AttributedStyle.RED :
                                   utilizationRate >= 100 ? AttributedStyle.YELLOW : AttributedStyle.GREEN;
            sevenDayLine.style(AttributedStyle.DEFAULT.foreground(weeklyRatioColor))
                    .append(String.format(" R:%3.0f%%", utilizationRate));

            if (sevenDay.resetsAt() != null) {
                sevenDayLine.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE))
                        .append(" -> " + formatWeeklyResetTime(sevenDay.resetsAt()));
            }

            terminal.writer().println(sevenDayLine.toAnsi());
        }

        terminal.writer().println();
        terminal.flush();
    }

    private double calculateTimeElapsedPercent(java.time.OffsetDateTime resetTimeUtc, long windowSeconds) {
        if (resetTimeUtc == null) {
            return 0.0;
        }

        var now = java.time.OffsetDateTime.now();
        var timeUntilReset = java.time.Duration.between(now, resetTimeUtc).getSeconds();
        var elapsedSeconds = windowSeconds - timeUntilReset;

        return (elapsedSeconds * 100.0) / windowSeconds;
    }

    private String formatWeeklyResetTime(java.time.OffsetDateTime resetTimeUtc) {
        var resetLocal = resetTimeUtc.atZoneSameInstant(java.time.ZoneId.systemDefault());
        var resetDate = resetLocal.toLocalDate();
        var now = java.time.LocalDate.now();
        var daysUntil = java.time.temporal.ChronoUnit.DAYS.between(now, resetDate);

        return String.format("%s (%d day%s)",
            resetDate.toString(),
            daysUntil,
            daysUntil == 1 ? "" : "s"
        );
    }

    private void printSummary(List<AccountUsage> accountUsages) {
        if (accountUsages.isEmpty()) {
            return;
        }

        terminal.writer().println("-".repeat(60));

        var avgUsage = accountUsages.stream()
                .mapToDouble(AccountUsage::getFiveHourUsagePercent)
                .average()
                .orElse(0.0);

        var avgRatio = accountUsages.stream()
                .mapToDouble(AccountUsage::getUsageToTimeRatio)
                .average()
                .orElse(0.0);

        var summary = new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.CYAN))
                .append("Summary: ")
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE))
                .append(String.format("Avg Usage %.0f%% | Avg Ratio %.0f%%", avgUsage, avgRatio * 100.0))
                .toAnsi();

        terminal.writer().println(summary);
        terminal.flush();
    }

    private void printError(String message) {
        var error = new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.RED))
                .append("[ERROR] ")
                .style(AttributedStyle.DEFAULT)
                .append(message)
                .toAnsi();

        terminal.writer().println(error);
        terminal.flush();
    }

    private void printWarning(String message) {
        var warning = new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW))
                .append("[WARNING] ")
                .style(AttributedStyle.DEFAULT)
                .append(message)
                .toAnsi();

        terminal.writer().println(warning);
        terminal.flush();
    }

    private void printInfo(String message) {
        var info = new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.CYAN))
                .append("[INFO] ")
                .style(AttributedStyle.DEFAULT)
                .append(message)
                .toAnsi();

        terminal.writer().println(info);
        terminal.flush();
    }

    private int getColorForUsage(double usage) {
        if (usage >= 80) return AttributedStyle.RED;
        if (usage >= 60) return AttributedStyle.YELLOW;
        return AttributedStyle.GREEN;
    }

    private int getColorForRatio(double ratio) {
        if (ratio >= 1.2) return AttributedStyle.RED;
        if (ratio >= 1.0) return AttributedStyle.YELLOW;
        return AttributedStyle.GREEN;
    }
}
