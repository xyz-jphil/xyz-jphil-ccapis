package xyz.jphil.ccapis.usage_tracker;

import xyz.jphil.ccapis.usage_tracker.cli.UsageTrackerCLI;
import xyz.jphil.ccapis.usage_tracker.ui.UsageWidget;

/**
 * Launcher for the Usage Tracker application.
 * Supports both CLI/TUI and JavaFX GUI modes.
 *
 * Usage:
 * - No args or --gui: Launch JavaFX floating widget
 * - --cli or --tui: Launch command-line interface
 * - --verbose, -v, or --debug: Enable verbose/debug logging
 */
public class UsageTrackerLauncher {

    public static void main(String[] args) {
        // Check if CLI mode is requested and parse flags
        var cliMode = false;
        var verbose = false;

        for (var arg : args) {
            var argLower = arg.toLowerCase();
            if ("--cli".equals(argLower) || "--tui".equals(argLower)) {
                cliMode = true;
            } else if ("--gui".equals(argLower) || "--widget".equals(argLower)) {
                cliMode = false;
            } else if ("--verbose".equals(argLower) || "-v".equals(argLower) || "--debug".equals(argLower)) {
                verbose = true;
            } else if ("--help".equals(argLower) || "-h".equals(argLower)) {
                printUsage();
                System.exit(0);
            } else if (!argLower.startsWith("--")) {
                System.err.println("Unknown argument: " + arg);
                printUsage();
                System.exit(1);
            }
        }

        // Set verbose mode as system property for child components to access
        if (verbose) {
            System.setProperty("xyz.jphil.ccapis.verbose", "true");
        }

        if (cliMode) {
            // Launch CLI mode
            System.out.println("Launching CLI mode" + (verbose ? " (verbose)" : "") + "...");
            UsageTrackerCLI.main(args);
        } else {
            // Launch JavaFX GUI mode (default)
            System.out.println("Launching JavaFX widget" + (verbose ? " (verbose)" : "") + "...");
            UsageWidget.main(args);
        }
    }

    private static void printUsage() {
        System.out.println("Usage Tracker - CCAPI Usage Monitoring");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar usage-tracker.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  (no args)         Launch JavaFX floating widget (default)");
        System.out.println("  --gui             Launch JavaFX floating widget");
        System.out.println("  --widget          Launch JavaFX floating widget");
        System.out.println("  --cli             Launch command-line interface (TUI)");
        System.out.println("  --tui             Launch command-line interface (TUI)");
        System.out.println("  --verbose, -v     Enable verbose/debug logging");
        System.out.println("  --debug           Enable verbose/debug logging");
        System.out.println("  --help, -h        Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar usage-tracker.jar");
        System.out.println("  java -jar usage-tracker.jar --gui");
        System.out.println("  java -jar usage-tracker.jar --cli");
        System.out.println("  java -jar usage-tracker.jar --cli --verbose");
    }
}
