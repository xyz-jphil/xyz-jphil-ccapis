package xyz.jphil.ccapis.usage_tracker.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.listener.UsageUpdateEvent;
import xyz.jphil.ccapis.model.CCAPIsCredentials;
import xyz.jphil.ccapis.ping.PingService;
import xyz.jphil.ccapis.usage_tracker.api.UsageApiClient;
import xyz.jphil.ccapis.usage_tracker.config.Settings;
import xyz.jphil.ccapis.usage_tracker.config.SettingsManager;
import xyz.jphil.ccapis.usage_tracker.model.AccountUsage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * JavaFX floating widget for usage tracking
 * Compact, always-on-top display with progress bars and system tray integration
 */
public class UsageWidget extends Application {

    // Removed: REFRESH_INTERVAL_SECONDS - now configurable via settings (usageRefreshIntervalSecs)
    private static final double WIDGET_WIDTH = 90; // Target: 100-125px
    private static final double TITLE_BAR_HEIGHT = 30;
    private static final double ROW_HEIGHT = 32;

    private VBox contentBox;
    private SettingsManager settingsManager;
    private CredentialsManager credentialsManager;
    private UsageApiClient apiClient;
    private Settings settings;
    private CCAPIsCredentials credentials;
    private Settings.UISettings uiSettings;
    private Stage primaryStage;
    private TrayIcon trayIcon;
    private PingService pingService;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        settingsManager = new SettingsManager();

        // Load UI settings
        try {
            settings = settingsManager.load();
            uiSettings = settings.getUiSettings();
        } catch (Exception e) {
            showError("Failed to load UI settings: " + e.getMessage());
            return;
        }

        // Load shared credentials
        credentialsManager = new CredentialsManager();
        try {
            credentials = credentialsManager.load();
        } catch (Exception e) {
            showError("Failed to load credentials: " + e.getMessage());
            return;
        }

        // Validate credentials
        if (credentials.getTrackUsageCredentials().isEmpty()) {
            showError("No credentials with trackUsage=true found");
            return;
        }

        // Create API client (stateless - baseUrl is per-credential)
        apiClient = new UsageApiClient();

        // Create and initialize ping service
        pingService = new PingService(
                uiSettings.pingIntervalDurSec(),
                uiSettings.pingMessageVisible()
        );
        System.out.println("Ping service initialized: interval=" + uiSettings.pingIntervalDurSec() + "s, visible=" + uiSettings.pingMessageVisible());

        // Create UI - minimal padding per PRP requirements
        contentBox = new VBox(2);
        contentBox.setPadding(new Insets(1));
        contentBox.setStyle("-fx-background-color: rgba(30, 30, 30, 0.95); -fx-background-radius: 5;");

        var scene = new Scene(contentBox, WIDGET_WIDTH, 100);
        scene.setFill(Color.TRANSPARENT);

        // Configure stage
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setAlwaysOnTop(true);
        primaryStage.setTitle("CCAPI Usage Tracker");
        primaryStage.setScene(scene);

        // Setup system tray
        setupSystemTray();

        // Handle window close request
        primaryStage.setOnCloseRequest(event -> {
            event.consume(); // Prevent default close
            saveWindowPosition(); // Save position before hiding
            hideToTray();
        });

        // Set window position from settings before showing
        setWindowPosition(primaryStage);

        // Show loading placeholder immediately (so window is visible and draggable)
        showLoadingPlaceholder();

        // Show window first (before any network calls)
        primaryStage.show();

        // Initial update - run asynchronously to avoid blocking UI thread
        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.submit(this::updateUsageData);

        // Schedule periodic updates using configurable refresh interval
        var refreshInterval = uiSettings.usageRefreshIntervalSecs();
        executor.scheduleAtFixedRate(
                this::updateUsageData,
                refreshInterval,
                refreshInterval,
                TimeUnit.SECONDS
        );
    }

    private void setupSystemTray() {
        // Check if system tray is supported
        if (!SystemTray.isSupported()) {
            System.err.println("System tray is not supported");
            return;
        }

        Platform.setImplicitExit(false); // Keep app running when window is hidden

        // Create tray icon image
        var trayImage = createTrayIcon();

        // Create popup menu
        var popup = new PopupMenu();

        var showItem = new MenuItem("Show");
        showItem.addActionListener(e -> Platform.runLater(this::showFromTray));

        var hideItem = new MenuItem("Hide");
        hideItem.addActionListener(e -> Platform.runLater(this::hideToTray));

        var settingsItem = new MenuItem("Open Settings File");
        settingsItem.addActionListener(e -> openSettingsInNotepad());

        var exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            Platform.runLater(() -> {
                if (pingService != null) {
                    pingService.shutdown();
                }
                SystemTray.getSystemTray().remove(trayIcon);
                Platform.exit();
                System.exit(0);
            });
        });

        popup.add(showItem);
        popup.add(hideItem);
        popup.addSeparator();
        popup.add(settingsItem);
        popup.addSeparator();
        popup.add(exitItem);

        // Create tray icon
        trayIcon = new TrayIcon(trayImage, "CCAPI Usage Tracker", popup);
        trayIcon.setImageAutoSize(true);

        // Double-click to show/hide
        trayIcon.addActionListener(e -> Platform.runLater(() -> {
            if (primaryStage.isShowing()) {
                hideToTray();
            } else {
                showFromTray();
            }
        }));

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.err.println("Failed to add tray icon: " + e.getMessage());
        }
    }

    private Image createTrayIcon() {
        // Create a simple colored circle as tray icon
        var size = 16;
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();

        // Enable antialiasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw a blue circle
        g.setColor(new java.awt.Color(0, 191, 255));
        g.fillOval(0, 0, size, size);

        // Draw a white border
        g.setColor(java.awt.Color.WHITE);
        g.drawOval(0, 0, size - 1, size - 1);

        g.dispose();
        return image;
    }

    private void hideToTray() {
        primaryStage.hide();
    }

    private void showFromTray() {
        primaryStage.show();
        primaryStage.toFront();
    }

    /**
     * Show loading placeholder with header so window is immediately visible and draggable
     */
    private void showLoadingPlaceholder() {
        contentBox.getChildren().clear();

        // Create header (same as in displayUsageData)
        var headerBox = createHeaderBox();
        contentBox.getChildren().add(headerBox);

        // Add loading message
        var loadingLabel = new Label("Loading...");
        loadingLabel.setStyle("-fx-text-fill: #00BFFF; -fx-font-size: 9px; -fx-padding: 10;");
        contentBox.getChildren().add(loadingLabel);
    }

    /**
     * Create header box with title and buttons
     */
    private HBox createHeaderBox() {
        var headerBox = new HBox(3);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(2));
        headerBox.setPrefHeight(TITLE_BAR_HEIGHT);
        headerBox.setMinHeight(TITLE_BAR_HEIGHT);
        headerBox.setMaxHeight(TITLE_BAR_HEIGHT);

        var title = new Label("CCUsage");
        title.setStyle("-fx-text-fill: #00BFFF; -fx-font-size: 10px; -fx-font-weight: bold;");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var hideBtn = new Button("−");
        hideBtn.setStyle("-fx-background-color: #404040; -fx-text-fill: white; " +
                "-fx-font-size: 10px; -fx-padding: 0 4 0 4; -fx-cursor: hand;");
        hideBtn.setOnAction(e -> hideToTray());

        var closeBtn = new Button("×");
        closeBtn.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; " +
                "-fx-font-size: 12px; -fx-padding: 0 4 0 4; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> {
            if (pingService != null) {
                pingService.shutdown();
            }
            SystemTray.getSystemTray().remove(trayIcon);
            Platform.exit();
            System.exit(0);
        });

        headerBox.getChildren().addAll(title, spacer, hideBtn, closeBtn);

        // Make header draggable
        makeDraggable(primaryStage, headerBox);

        return headerBox;
    }

    private void updateUsageData() {
        var activeCredentials = credentials.getTrackUsageCredentials();
        if (activeCredentials.isEmpty()) {
            Platform.runLater(() -> showError("No credentials with trackUsage=true found"));
            return;
        }

        var accountUsages = new ArrayList<AccountUsage>();
        for (var credential : activeCredentials) {
            try {
                var usageData = apiClient.fetchUsageWithRetry(credential, 3);
                var accountUsage = new AccountUsage(credential, usageData);
                accountUsages.add(accountUsage);

                // Fire usage update event to ping service
                if (pingService != null) {
                    var event = new UsageUpdateEvent()
                            .credential(credential)
                            .usageData(usageData)
                            .timestamp(System.currentTimeMillis());
                    pingService.onUsageUpdate(event);
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch usage for: " + credential.id() + " - " + e.getMessage());

                // Fire event even on failure (with null usageData)
                if (pingService != null) {
                    var event = new UsageUpdateEvent()
                            .credential(credential)
                            .usageData(null)
                            .timestamp(System.currentTimeMillis());
                    pingService.onUsageUpdate(event);
                }
            }
        }

        Platform.runLater(() -> displayUsageData(accountUsages));
    }

    private void displayUsageData(List<AccountUsage> accountUsages) {
        contentBox.getChildren().clear();

        // Add header
        var headerBox = createHeaderBox();
        contentBox.getChildren().add(headerBox);

        // Account rows
        for (var accountUsage : accountUsages) {
            var row = createAccountRow(accountUsage);
            contentBox.getChildren().add(row);
        }

        // Adjust window height - title bar + rows
        var newHeight = TITLE_BAR_HEIGHT + (accountUsages.size() * ROW_HEIGHT) + 10;
        primaryStage.setHeight(newHeight);
    }

    private HBox createAccountRow(AccountUsage accountUsage) {
        // Single horizontal row per PRP requirements
        var row = new HBox(1);
        row.setPadding(new Insets(1));
        row.setStyle("-fx-background-color: rgba(50, 50, 50, 0.8); -fx-background-radius: 2;");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefHeight(ROW_HEIGHT);
        row.setMinHeight(ROW_HEIGHT);
        row.setMaxHeight(ROW_HEIGHT);

        // 1. Account ID + Tier (stacked vertically) - FIXED WIDTH for alignment
        var idTierBox = new VBox(0);
        idTierBox.setAlignment(Pos.CENTER_LEFT);
        idTierBox.setPadding(new Insets(0, 1, 0, 2));
        // Fixed width based on idMaxLen to ensure grid alignment
        var idWidth = uiSettings.idMaxLen() * 7.5; // Width per character (monospace estimate)
        idTierBox.setMinWidth(idWidth);
        idTierBox.setPrefWidth(idWidth);
        idTierBox.setMaxWidth(idWidth);

        // Truncate ID based on settings
        var fullId = accountUsage.credential().id();
        var displayId = fullId.length() > uiSettings.idMaxLen()
            ? fullId.substring(0, uiSettings.idMaxLen())
            : fullId;

        var accountIdLabel = new Label(displayId);
        accountIdLabel.setStyle("-fx-text-fill: white; -fx-font-size: 7px; -fx-font-weight: bold;");

        // Show tier with better visibility
        var tier = accountUsage.credential().tier();
        var tierLabel = new Label(tier == 0 ? "F" : String.valueOf(tier)); // F for free tier
        tierLabel.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 7px;");

        idTierBox.getChildren().addAll(accountIdLabel, tierLabel);

        // 2. Progress indicators VBox (usage on top, time below)
        var progressBox = new VBox(1);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(Insets.EMPTY);

        // Get color based on usage percentage
        var usagePercent = accountUsage.getFiveHourUsagePercent();
        var usageColor = getProgressColorFromPercent(usagePercent);

        // Usage progress (top)
        var usageProgress = new ProgressIndicatorComponent(
            usagePercent,
            100.0,
            usageColor
        );

        // Time progress (below) - always blue
        var timeProgress = new ProgressIndicatorComponent(
            accountUsage.getFiveHourTimeElapsedPercent(),
            100.0,
            "#00BFFF"
        );

        progressBox.getChildren().addAll(usageProgress, timeProgress);

        // 3. Metrics VBox (utilization rate on top, time info below)
        var metricsBox = new VBox(0);
        metricsBox.setAlignment(Pos.CENTER);
        metricsBox.setPadding(Insets.EMPTY);

        // Utilization Rate (usage/time as percentage)
        var utilizationRate = accountUsage.getUsageToTimeRatio() * 100.0;
        var rateLabel = new Label(String.format("%.0f%%", utilizationRate));
        var rateColor = getUtilizationColor(utilizationRate);
        rateLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: 7px; -fx-font-weight: bold;",
            rateColor
        ));
        rateLabel.setAlignment(Pos.CENTER);

        // Time info based on settings (resetTime or timeRemaining)
        var timeText = "resetTime".equals(uiSettings.showTime())
            ? accountUsage.getCycleResetTime()
            : accountUsage.getTimeUntilReset();
        var timeLabel = new Label(timeText);
        timeLabel.setStyle("-fx-text-fill: #00BFFF; -fx-font-size: 7px;");
        timeLabel.setAlignment(Pos.CENTER);

        metricsBox.getChildren().addAll(rateLabel, timeLabel);

        row.getChildren().addAll(idTierBox, progressBox, metricsBox);

        // Add hover tooltip with detailed status
        installDetailedTooltip(row, accountUsage);

        return row;
    }

    /**
     * Installs a detailed tooltip on hover showing expanded usage statistics.
     *
     * @param node The node to attach the tooltip to
     * @param accountUsage Account usage data
     */
    private void installDetailedTooltip(HBox node, AccountUsage accountUsage) {
        var tooltip = new Tooltip();
        tooltip.setShowDelay(Duration.millis(300));
        tooltip.setHideDelay(Duration.millis(5000));
        tooltip.setStyle("-fx-font-size: 10px; -fx-background-color: rgba(40, 40, 40, 0.95);");

        // Build detailed tooltip content
        var sb = new StringBuilder();
        sb.append("Account: ").append(accountUsage.credential().id()).append("\n");
        sb.append("Name: ").append(accountUsage.credential().name()).append("\n");
        sb.append("Email: ").append(accountUsage.credential().email()).append("\n");
        sb.append("\n");
        sb.append("5-Hour Window:\n");
        sb.append("  Usage: ").append(String.format("%.1f%%", accountUsage.getFiveHourUsagePercent())).append("\n");
        sb.append("  Time Elapsed: ").append(String.format("%.1f%%", accountUsage.getFiveHourTimeElapsedPercent())).append("\n");
        sb.append("  Utilization Rate: ").append(String.format("%.1f%%", accountUsage.getUsageToTimeRatio() * 100.0)).append("\n");
        sb.append("  Resets In: ").append(accountUsage.getTimeUntilReset()).append("\n");

        // Add more details if available from usageData
        var usageData = accountUsage.usageData();
        if (usageData != null) {
            if (usageData.sevenDay() != null) {
                var sevenDay = usageData.sevenDay();
                sb.append("\n");
                sb.append("7-Day Window:\n");
                sb.append("  Usage: ").append(String.format("%.1f%%", ((float)sevenDay.utilization()))).append("\n");
                var timeElapsed = calculateTimeElapsedPercent(sevenDay.resetsAt(), 7 * 24 * 60 * 60);
                sb.append("  Time Elapsed: ").append(String.format("%.1f%%", timeElapsed)).append("\n");
                var utilizationRate = timeElapsed > 0 ? (sevenDay.utilization() / timeElapsed) * 100.0 : 0.0;
                sb.append("  Utilization Rate: ").append(String.format("%.1f%%", utilizationRate)).append("\n");
                if (sevenDay.resetsAt() != null) {
                    sb.append("  Resets: ").append(formatWeeklyResetTime(sevenDay.resetsAt())).append("\n");
                }
            }
            if (usageData.sevenDayOpus() != null) {
                var opus = usageData.sevenDayOpus();
                sb.append("\n");
                sb.append("7-Day Opus:\n");
                sb.append("  Usage: ").append(String.format("%.1f%%", ((float)opus.utilization()))).append("\n");
                var timeElapsed = calculateTimeElapsedPercent(opus.resetsAt(), 7 * 24 * 60 * 60);
                sb.append("  Time Elapsed: ").append(String.format("%.1f%%", timeElapsed)).append("\n");
                var utilizationRate = timeElapsed > 0 ? (opus.utilization() / timeElapsed) * 100.0 : 0.0;
                sb.append("  Utilization Rate: ").append(String.format("%.1f%%", utilizationRate)).append("\n");
                if (opus.resetsAt() != null) {
                    sb.append("  Resets: ").append(formatWeeklyResetTime(opus.resetsAt())).append("\n");
                }
            }
            if (usageData.sevenDayOauthApps() != null) {
                var oauth = usageData.sevenDayOauthApps();
                sb.append("\n");
                sb.append("7-Day OAuth Apps:\n");
                sb.append("  Usage: ").append(String.format("%.1f%%", ((float)oauth.utilization()))).append("\n");
                var timeElapsed = calculateTimeElapsedPercent(oauth.resetsAt(), 7 * 24 * 60 * 60);
                sb.append("  Time Elapsed: ").append(String.format("%.1f%%", timeElapsed)).append("\n");
                var utilizationRate = timeElapsed > 0 ? (oauth.utilization() / timeElapsed) * 100.0 : 0.0;
                sb.append("  Utilization Rate: ").append(String.format("%.1f%%", utilizationRate)).append("\n");
                if (oauth.resetsAt() != null) {
                    sb.append("  Resets: ").append(formatWeeklyResetTime(oauth.resetsAt())).append("\n");
                }
            }
        }

        tooltip.setText(sb.toString());
        Tooltip.install(node, tooltip);
    }

    /**
     * Format weekly reset time as "YYYY-MM-DD (X days)"
     */
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

    /**
     * Calculate time elapsed percentage for any window duration
     *
     * @param resetTimeUtc Reset time in UTC
     * @param windowSeconds Total window duration in seconds
     * @return Time elapsed percentage (0-100)
     */
    private double calculateTimeElapsedPercent(java.time.OffsetDateTime resetTimeUtc, long windowSeconds) {
        if (resetTimeUtc == null) {
            return 0.0;
        }

        var now = java.time.OffsetDateTime.now();
        var timeUntilReset = java.time.Duration.between(now, resetTimeUtc).getSeconds();
        var elapsedSeconds = windowSeconds - timeUntilReset;

        return (elapsedSeconds * 100.0) / windowSeconds;
    }

    /**
     * Gets color based on usage percentage.
     *
     * @param percentage Usage percentage (0-100)
     * @return CSS color string
     */
    private String getProgressColorFromPercent(double percentage) {
        if (percentage >= 80) return "#FF4444"; // Red
        if (percentage >= 60) return "#FFAA00"; // Orange
        return "#44FF44"; // Green
    }

    /**
     * Gets color based on utilization rate percentage.
     * Utilization rate = (usage / time elapsed) * 100
     *
     * @param rate Utilization rate percentage
     * @return CSS color string
     */
    private String getUtilizationColor(double rate) {
        if (rate >= 120) return "#FF4444"; // Red - using too fast
        if (rate >= 100) return "#FFAA00"; // Orange - right at the edge
        return "#44FF44"; // Green - using slower than time passing
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            contentBox.getChildren().clear();
            var errorLabel = new Label("Error: " + message);
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
            contentBox.getChildren().add(errorLabel);
        });
    }

    private void makeDraggable(Stage stage, HBox node) {
        final double[] xOffset = {0};
        final double[] yOffset = {0};

        node.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });

        node.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset[0]);
            stage.setY(event.getScreenY() - yOffset[0]);
        });

        // Save position when drag ends
        node.setOnMouseReleased(event -> {
            saveWindowPosition();
        });
    }

    /**
     * Set window position from settings (lastScreenLoc)
     * Validates position is within current screen bounds
     */
    private void setWindowPosition(Stage stage) {
        if (uiSettings.lastScreenLoc() != null && !uiSettings.lastScreenLoc().isEmpty()) {
            try {
                var parts = uiSettings.lastScreenLoc().split(",");
                if (parts.length == 2) {
                    var x = Double.parseDouble(parts[0].trim());
                    var y = Double.parseDouble(parts[1].trim());

                    // Get screen bounds
                    var screen = javafx.stage.Screen.getPrimary();
                    var bounds = screen.getVisualBounds();

                    // Validate position is within screen bounds
                    // If beyond screen, reset to center
                    if (x < bounds.getMinX() || x > bounds.getMaxX() - stage.getWidth() ||
                        y < bounds.getMinY() || y > bounds.getMaxY() - stage.getHeight()) {
                        // Position is invalid, use center of screen
                        x = bounds.getMinX() + (bounds.getWidth() - WIDGET_WIDTH) / 2;
                        y = bounds.getMinY() + (bounds.getHeight() - 100) / 2;
                        System.out.println("Window position beyond screen bounds, resetting to center");

                        // Save the corrected position
                        uiSettings.lastScreenLoc(String.format("%.0f,%.0f", x, y));
                        saveSettings();
                    }

                    stage.setX(x);
                    stage.setY(y);
                }
            } catch (Exception e) {
                System.err.println("Failed to parse lastScreenLoc: " + e.getMessage());
            }
        }
    }

    /**
     * Save current window position to settings
     */
    private void saveWindowPosition() {
        if (primaryStage != null) {
            var x = primaryStage.getX();
            var y = primaryStage.getY();
            uiSettings.lastScreenLoc(String.format("%.0f,%.0f", x, y));
            saveSettings();
        }
    }

    /**
     * Save settings to file
     */
    private void saveSettings() {
        try {
            settingsManager.save(settings);
        } catch (Exception e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    /**
     * Open CCAPIsUsageTrackerSettings.xml in system text editor (cross-platform)
     */
    private void openSettingsInNotepad() {
        try {
            if (Desktop.isDesktopSupported()) {
                var desktop = Desktop.getDesktop();
                var file = settingsManager.getSettingsPath().toFile();

                if (desktop.isSupported(Desktop.Action.EDIT)) {
                    desktop.edit(file);
                } else if (desktop.isSupported(Desktop.Action.OPEN)) {
                    // Fallback to opening with default app if EDIT not supported
                    desktop.open(file);
                } else {
                    System.err.println("Neither EDIT nor OPEN action supported on this platform");
                }
            } else {
                System.err.println("Desktop not supported on this platform");
            }
        } catch (Exception e) {
            System.err.println("Failed to open settings file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
