package xyz.jphil.ccapis.usage_tracker.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.listener.UsageUpdateEvent;
import xyz.jphil.ccapis.model.CCAPICredential;
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
    private static final double WIDGET_WIDTH = 105; // Increased to accommodate scrollbar (90 + 15 for scrollbar)
    private static final double TITLE_BAR_HEIGHT = 30;
    private static final double ROW_HEIGHT = 32;
    private static final double MIN_HEIGHT = 100;
    private static final double MAX_HEIGHT = 400;

    private VBox contentBox;
    private VBox accountsContainer;
    private ScrollPane scrollPane;
    private SettingsManager settingsManager;
    private CredentialsManager credentialsManager;
    private UsageApiClient apiClient;
    private Settings settings;
    private CCAPIsCredentials credentials;
    private Settings.UISettings uiSettings;
    private Stage primaryStage;
    private TrayIcon trayIcon;
    private PingService pingService;
    private int lastAccountCount = -1; // Track account count to avoid unnecessary resizing
    private boolean userManuallyResized = false; // Track if user manually resized window
    private boolean programmaticHeightChange = false; // Track if height change is programmatic

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
        contentBox = new VBox(0);
        contentBox.setPadding(new Insets(1, 1, 3, 1)); // Extra padding at bottom for resize area
        contentBox.setStyle("-fx-background-color: rgba(30, 30, 30, 0.95); -fx-background-radius: 5;");

        // Create accounts container (will hold all account rows)
        accountsContainer = new VBox(0);
        accountsContainer.setPadding(Insets.EMPTY);

        // Create scroll pane for accounts
        scrollPane = new ScrollPane(accountsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPadding(Insets.EMPTY);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Style scrollbar to match theme - thin dark scrollbar with blue thumb
        scrollPane.setStyle(
            "-fx-background: transparent; " +
            "-fx-background-color: transparent; " +
            "-fx-focus-color: transparent; " +
            "-fx-faint-focus-color: transparent;"
        );

        // Apply custom CSS for scrollbar styling
        var scrollbarCss = getClass().getResource("/xyz/jphil/ccapis/usage_tracker/ui/scrollbar.css");
        if (scrollbarCss != null) {
            scrollPane.getStylesheets().add(scrollbarCss.toExternalForm());
        }

        var scene = new Scene(contentBox, WIDGET_WIDTH, MIN_HEIGHT);
        scene.setFill(Color.TRANSPARENT);

        // Configure stage
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setAlwaysOnTop(true);
        primaryStage.setTitle("CCAPI Usage Tracker");
        primaryStage.setScene(scene);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.setMaxHeight(MAX_HEIGHT);
        primaryStage.setResizable(true);

        // Lock width, only allow vertical resizing
        primaryStage.setMinWidth(WIDGET_WIDTH);
        primaryStage.setMaxWidth(WIDGET_WIDTH);

        // Save window height when user resizes (but not on programmatic changes)
        primaryStage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (primaryStage.isShowing() && !oldVal.equals(newVal) && !programmaticHeightChange) {
                saveWindowPosition();
            }
        });

        // Enable custom resize since transparent windows don't have native resize handles
        // Bottom edge resize on contentBox
        enableResizeBottom(primaryStage, contentBox);

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

        // Add loading message to accounts container
        accountsContainer.getChildren().clear();
        var loadingLabel = new Label("Loading...");
        loadingLabel.setStyle("-fx-text-fill: #00BFFF; -fx-font-size: 9px; -fx-padding: 10;");
        accountsContainer.getChildren().add(loadingLabel);

        // Add scroll pane to content box
        if (!contentBox.getChildren().contains(scrollPane)) {
            contentBox.getChildren().add(scrollPane);
        }
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

        // Make header both draggable and resizable (combined functionality)
        makeHeaderInteractive(primaryStage, headerBox);

        return headerBox;
    }

    /**
     * Refresh usage data for a single account only.
     * Updates the UI for just this account without fetching data for others.
     */
    private void refreshSingleAccount(CCAPICredential credential, HBox rowContainer, UsageWidgetController controller) {
        var credId = credential.id();
        System.out.println("\n┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│ 🔄 SINGLE ACCOUNT REFRESH - " + credId + "                          │");
        System.out.println("└─────────────────────────────────────────────────────────────┘");

        // Validate credential
        var baseUrl = credential.resolvedCcapiBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            System.err.println("  ⚠️  Cannot refresh - ccapiBaseUrl is not set");
            return;
        }

        /*if (!credential.active()) {
            System.out.println(" Forced refresh - credential is inactive");
            return;
        }*/

        try {
            System.out.println("  🌐 Fetching from: " + baseUrl);
            var usageData = apiClient.fetchUsageWithRetry(credential, 3);
            var accountUsage = new AccountUsage(credential, usageData);

            // Show usage info
            if (usageData != null && usageData.fiveHour() != null) {
                System.out.println("  ✅ Success - Usage: " + String.format("%.1f%%", usageData.fiveHour().utilization()));
            } else {
                System.out.println("  ✅ Success - No usage data available");
            }

            // Update the UI on JavaFX thread
            Platform.runLater(() -> {
                controller.updateUsageData(accountUsage, uiSettings.showTime());
            });

            // Fire usage update event to ping service
            if (pingService != null) {
                var event = new UsageUpdateEvent()
                        .credential(credential)
                        .usageData(usageData)
                        .timestamp(System.currentTimeMillis());
                pingService.onUsageUpdate(event);
            }

            System.out.println("  ✅ Account refreshed successfully");
        } catch (Exception e) {
            System.err.println("  ❌ Failed - " + e.getMessage());

            // Update UI to show error state
            Platform.runLater(() -> {
                var accountUsage = new AccountUsage(credential, null);
                controller.updateUsageData(accountUsage, uiSettings.showTime());
            });
        }
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
    }

    private void updateUsageData() {
        var timestamp = java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        );

        // DEBUG: Show where this call came from
        var stackTrace = Thread.currentThread().getStackTrace();
        var caller = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "unknown";

        System.out.println("\n┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│ 📊 USAGE DATA UPDATE STARTED - " + timestamp + "                  │");
        System.out.println("│ 🔍 Called from: " + caller + "                                    │");
        System.out.println("└─────────────────────────────────────────────────────────────┘");

        var trackUsageCredentials = credentials.getTrackUsageCredentials();
        if (trackUsageCredentials.isEmpty()) {
            System.err.println("❌ No credentials with trackUsage=true found");
            Platform.runLater(() -> showError("No credentials with trackUsage=true found"));
            return;
        }

        System.out.println("Found " + trackUsageCredentials.size() + " credential(s) with trackUsage=true");

        // DEBUG: Check for duplicate credentials in the list
        var credIds = trackUsageCredentials.stream().map(c -> c.id()).toList();
        var uniqueCredIds = new java.util.HashSet<>(credIds);
        if (credIds.size() != uniqueCredIds.size()) {
            System.err.println("⚠️  WARNING: Duplicate credentials detected in trackUsage list!");
            System.err.println("   Total: " + credIds.size() + ", Unique: " + uniqueCredIds.size());
            System.err.println("   IDs: " + credIds);
        }
        System.out.println();

        var accountUsages = new ArrayList<AccountUsage>();
        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        for (var credential : trackUsageCredentials) {
            var credId = credential.id();
            System.out.println("Processing: " + credId);

            // Validate credential before attempting to fetch
            var baseUrl = credential.resolvedCcapiBaseUrl();
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                System.err.println("  ⚠️  Skipped - ccapiBaseUrl is not set (check credentials.xml)");
                skipCount++;
                System.out.println();
                continue; // Don't even show this credential in UI
            }

            // even if credential is inactive ... 
            // we should be able to monitor it if trackUsage="true" 
            // (if trackUsage="true" is not even true ... 
            // then only it doesn't list in our ui and is out of scope for us)
            // we have already filtered and only have credentials where trackUsage is true
            // so there is nothing more to filter.
            if (false /*!credential.active()*/ ) { 
                    
                
                System.out.println("  ⏸️  Skipped - credential is inactive (active=false)");
                // Create AccountUsage with null data to show in UI as inactive
                var accountUsage = new AccountUsage(credential, null);
                accountUsages.add(accountUsage);
                System.out.println("  📝 Added to list (inactive) - Total in list: " + accountUsages.size());
                skipCount++;
                System.out.println();
                continue;
            }

            try {
                System.out.println("  🌐 Fetching from: " + baseUrl);
                var usageData = apiClient.fetchUsageWithRetry(credential, 3);
                var accountUsage = new AccountUsage(credential, usageData);

                // Show basic usage info (might throw exception if data is malformed)
                if (usageData != null && usageData.fiveHour() != null) {
                    System.out.println("  ✅ Success - Usage: " + String.format("%.1f%%", usageData.fiveHour().utilization()));
                } else {
                    System.out.println("  ✅ Success - No usage data available");
                }

                // Fire usage update event to ping service (might also throw)
                if (pingService != null) {
                    var event = new UsageUpdateEvent()
                            .credential(credential)
                            .usageData(usageData)
                            .timestamp(System.currentTimeMillis());
                    pingService.onUsageUpdate(event);
                }

                // IMPORTANT: Only add to list AFTER all operations that might throw exceptions
                // This prevents duplicates when parsing fails after fetch succeeds
                accountUsages.add(accountUsage);
                System.out.println("  📝 Added to list (success) - Total in list: " + accountUsages.size());
                successCount++;
            } catch (Exception e) {
                System.err.println("  ❌ Failed - " + e.getMessage());
                failCount++;

                // Create AccountUsage with null data to show error state in UI
                var accountUsage = new AccountUsage(credential, null);
                accountUsages.add(accountUsage);
                System.out.println("  📝 Added to list (failed) - Total in list: " + accountUsages.size());

                // Fire event even on failure (with null usageData)
                if (pingService != null) {
                    var event = new UsageUpdateEvent()
                            .credential(credential)
                            .usageData(null)
                            .timestamp(System.currentTimeMillis());
                    pingService.onUsageUpdate(event);
                }
            }
            System.out.println();
        }

        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│ UPDATE SUMMARY                                              │");
        System.out.println("├─────────────────────────────────────────────────────────────┤");
        System.out.println("│  ✅ Successful: " + String.format("%-2d", successCount) + "                                          │");
        System.out.println("│  ⏸️  Skipped:    " + String.format("%-2d", skipCount) + "                                          │");
        System.out.println("│  ❌ Failed:     " + String.format("%-2d", failCount) + "                                          │");
        System.out.println("│  📊 Total:      " + String.format("%-2d", trackUsageCredentials.size()) + "                                          │");
        System.out.println("└─────────────────────────────────────────────────────────────┘");

        // DEBUG: Show final account list being sent to UI
        System.out.println("\n📋 Final AccountUsages list (" + accountUsages.size() + " entries):");
        for (int i = 0; i < accountUsages.size(); i++) {
            var au = accountUsages.get(i);
            var hasData = au.usageData() != null;
            System.out.println("  [" + i + "] " + au.credential().id() +
                " - Active: " + au.credential().active() +
                " - HasData: " + hasData);
        }
        System.out.println();

        Platform.runLater(() -> displayUsageData(accountUsages));
    }

    private void displayUsageData(List<AccountUsage> accountUsages) {
        contentBox.getChildren().clear();

        // Add header
        var headerBox = createHeaderBox();
        contentBox.getChildren().add(headerBox);

        // Add scroll pane
        contentBox.getChildren().add(scrollPane);

        // Clear and populate accounts container
        accountsContainer.getChildren().clear();

        // Account rows with separators
        for (int i = 0; i < accountUsages.size(); i++) {
            var accountUsage = accountUsages.get(i);
            var row = createAccountRow(accountUsage);
            accountsContainer.getChildren().add(row);

            // Add separator after each row except the last one
            if (i < accountUsages.size() - 1) {
                var separator = createSeparator();
                accountsContainer.getChildren().add(separator);
            }
        }

        // Only adjust window height if:
        // 1. First time displaying (lastAccountCount == -1), OR
        // 2. Account count has changed, AND
        // 3. User has not manually resized the window
        if (!userManuallyResized && (lastAccountCount == -1 || lastAccountCount != accountUsages.size())) {
            var visibleRows = Math.min(accountUsages.size(), 5);
            var newHeight = TITLE_BAR_HEIGHT + (visibleRows * ROW_HEIGHT) + (visibleRows - 1) * 2 + 10; // +2 per separator
            newHeight = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, newHeight));

            // Mark as programmatic change to prevent save triggering
            programmaticHeightChange = true;
            primaryStage.setHeight(newHeight);
            programmaticHeightChange = false;

            lastAccountCount = accountUsages.size();
        }
    }

    /**
     * Create a thin colored separator line between account rows
     */
    private Region createSeparator() {
        var separator = new Region();
        separator.setPrefHeight(2);
        separator.setMinHeight(2);
        separator.setMaxHeight(2);
        separator.setStyle("-fx-background-color: linear-gradient(to right, transparent, #00BFFF, transparent);");
        return separator;
    }

    /**
     * Create account row using FXML-based UI.
     * This is the new implementation that uses the hand-made FXML.
     */
    private HBox createAccountRow(AccountUsage accountUsage) {
        try {
            // Load FXML
            var fxmlLoader = new FXMLLoader(
                getClass().getResource("/xyz/jphil/ccapis/usage_tracker/ui/usage-widget-row.fxml")
            );
            HBox row = fxmlLoader.load();

            // Get controller
            UsageWidgetController controller = fxmlLoader.getController();

            // Set root container for background color changes
            controller.setRootContainer(row);

            // Set up callback handlers
            controller.setOnRefresh(() -> {
                var credId = accountUsage.credential().id();
                var timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                );

                System.out.println("═══════════════════════════════════════════════════════════");
                System.out.println("🔄 REFRESH BUTTON CLICKED");
                System.out.println("  Account: " + credId);
                System.out.println("  Time: " + timestamp);
                System.out.println("  Action: Refreshing ONLY this account...");
                System.out.println("═══════════════════════════════════════════════════════════");

                // Set button to busy state (red, disabled)
                controller.setRefreshButtonBusy(true);

                try {
                    // Refresh only this specific account
                    refreshSingleAccount(accountUsage.credential(), row, controller);
                } finally {
                    // Restore button state (normal, enabled)
                    controller.setRefreshButtonBusy(false);
                }
            });

            controller.setOnPing(() -> {
                var credId = accountUsage.credential().id();
                var timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                );
                var credName = accountUsage.credential().name();
                var credEmail = accountUsage.credential().email();
                var pingEnabled = accountUsage.credential().ping();

                System.out.println("═══════════════════════════════════════════════════════════");
                System.out.println("🔔 PING BUTTON CLICKED");
                System.out.println("  Account ID: " + credId);
                System.out.println("  Name: " + credName);
                System.out.println("  Email: " + credEmail);
                System.out.println("  Time: " + timestamp);
                System.out.println("  Ping Enabled (ping attr): " + pingEnabled);

                // Check if ping is disabled for this credential
                if (!pingEnabled) {
                    System.out.println("  ⚠️  WARNING: ping=\"false\" in credentials.xml");
                    System.out.println("  ⚠️  Manual ping button OVERRIDES this setting");
                }

                // Set button to busy state (red, disabled)
                controller.setPingButtonBusy(true);

                try {
                    // Trigger manual ping for this account (bypasses ping attribute check)
                    if (pingService != null) {
                        System.out.println("  Action: Sending manual ping...");

                        // Use manualPing() instead of onUsageUpdate() to bypass ping attribute check
                        pingService.manualPing(accountUsage.credential());

                        System.out.println("  Status: ✅ Manual ping completed");
                    } else {
                        System.out.println("  Status: ⚠️  Ping service not available");
                    }
                } catch (Exception e) {
                    System.err.println("  Status: ❌ Ping failed - " + e.getMessage());
                } finally {
                    System.out.println("═══════════════════════════════════════════════════════════");
                    // Restore button state (normal, enabled)
                    controller.setPingButtonBusy(false);
                }
            });

            // Update UI with data
            controller.updateUsageData(accountUsage, uiSettings.showTime());

            // Add hover tooltip with detailed status
            installDetailedTooltip(row, accountUsage);

            return row;
        } catch (Exception e) {
            System.err.println("Failed to load FXML for account row: " + e.getMessage());
            e.printStackTrace();
            // Fallback to deprecated programmatic UI
            return createAccountRowProgrammatic(accountUsage);
        }
    }

    /**
     * @deprecated Use {@link #createAccountRow(AccountUsage)} which uses FXML-based UI.
     * This method is kept for fallback purposes only.
     */
    @Deprecated
    private HBox createAccountRowProgrammatic(AccountUsage accountUsage) {
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
        tooltip.setShowDelay(Duration.millis(1000)); // 1 second delay before showing
        tooltip.setHideDelay(Duration.millis(100));  // Hide quickly after mouse leaves
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

    /**
     * Makes the header both draggable (for moving window) and resizable from top edge.
     * Detects if mouse is in resize zone (top 5px), otherwise allows dragging.
     */
    private void makeHeaderInteractive(Stage stage, HBox headerNode) {
        final double RESIZE_MARGIN = 5; // Pixels from top edge to trigger resize
        final double[] dragXOffset = {0};
        final double[] dragYOffset = {0};
        final double[] resizeStartY = {0};
        final double[] resizeStartHeight = {0};
        final double[] resizeStartStageY = {0};
        final boolean[] isResizing = {false};
        final boolean[] isDragging = {false};

        headerNode.setOnMouseMoved(event -> {
            // Use screen coordinates to detect if mouse is near top of window
            var mouseScreenY = event.getScreenY();
            var windowTop = stage.getY();

            // Change cursor when near top edge
            if (mouseScreenY <= windowTop + RESIZE_MARGIN) {
                headerNode.setCursor(javafx.scene.Cursor.N_RESIZE);
            } else {
                if (!isResizing[0] && !isDragging[0]) {
                    headerNode.setCursor(javafx.scene.Cursor.DEFAULT);
                }
            }
        });

        headerNode.setOnMousePressed(event -> {
            // Use screen coordinates to detect if clicked near top of window
            var mouseScreenY = event.getScreenY();
            var windowTop = stage.getY();

            // Start resize if clicked near top edge
            if (mouseScreenY <= windowTop + RESIZE_MARGIN) {
                isResizing[0] = true;
                resizeStartY[0] = event.getScreenY();
                resizeStartHeight[0] = stage.getHeight();
                resizeStartStageY[0] = stage.getY();
                event.consume();
            } else {
                // Start drag if NOT in resize zone
                isDragging[0] = true;
                dragXOffset[0] = event.getSceneX();
                dragYOffset[0] = event.getSceneY();
            }
        });

        headerNode.setOnMouseDragged(event -> {
            if (isResizing[0]) {
                // Handle resize
                var deltaY = event.getScreenY() - resizeStartY[0];
                var newHeight = resizeStartHeight[0] - deltaY; // Subtract because dragging up
                var newY = resizeStartStageY[0] + deltaY;

                // Clamp to min/max height
                if (newHeight >= MIN_HEIGHT && newHeight <= MAX_HEIGHT) {
                    stage.setHeight(newHeight);
                    stage.setY(newY);
                }
                event.consume();
            } else if (isDragging[0]) {
                // Handle drag (move window)
                stage.setX(event.getScreenX() - dragXOffset[0]);
                stage.setY(event.getScreenY() - dragYOffset[0]);
            }
        });

        headerNode.setOnMouseReleased(event -> {
            if (isResizing[0] || isDragging[0]) {
                if (isResizing[0]) {
                    userManuallyResized = true; // Mark that user manually resized
                }
                isResizing[0] = false;
                isDragging[0] = false;
                headerNode.setCursor(javafx.scene.Cursor.DEFAULT);
                saveWindowPosition(); // Save new position/height
            }
        });

        headerNode.setOnMouseExited(event -> {
            if (!isResizing[0] && !isDragging[0]) {
                headerNode.setCursor(javafx.scene.Cursor.DEFAULT);
            }
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
     * Set window position and height from settings (lastScreenLoc)
     * Format: "x,y" or "x,y,height"
     * Validates position is within current screen bounds
     */
    private void setWindowPosition(Stage stage) {
        System.out.println("📍 LOADING window position from settings: " + uiSettings.lastScreenLoc());
        if (uiSettings.lastScreenLoc() != null && !uiSettings.lastScreenLoc().isEmpty()) {
            try {
                var parts = uiSettings.lastScreenLoc().split(",");
                if (parts.length >= 2) {
                    var x = Double.parseDouble(parts[0].trim());
                    var y = Double.parseDouble(parts[1].trim());
                    var height = parts.length >= 3 ? Double.parseDouble(parts[2].trim()) : MIN_HEIGHT;
                    System.out.println("   Parsed: x=" + x + ", y=" + y + ", height=" + height);

                    // Clamp height to valid range
                    height = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height));

                    // Get screen bounds
                    var screen = javafx.stage.Screen.getPrimary();
                    var bounds = screen.getVisualBounds();

                    // Validate position has positive projection on ANY screen
                    // Simple overlap check - if window overlaps screen at all, it's valid
                    // This handles taskbars, multi-monitor setups, and negative coordinates

                    System.out.println("   Primary screen bounds: minX=" + bounds.getMinX() + ", maxX=" + bounds.getMaxX() +
                                     ", minY=" + bounds.getMinY() + ", maxY=" + bounds.getMaxY());

                    // Check all screens (multi-monitor setup)
                    var screens = javafx.stage.Screen.getScreens();
                    System.out.println("   🖥️  Detected " + screens.size() + " screen(s)");
                    var isVisibleOnAnyScreen = false;

                    for (int i = 0; i < screens.size(); i++) {
                        var scr = screens.get(i);

                        // Use FULL screen bounds (includes taskbar) instead of visual bounds (excludes taskbar)
                        var screenBounds = scr.getBounds();
                        var visualBounds = scr.getVisualBounds();

                        System.out.println("   🖥️  Screen #" + (i+1) + ":");
                        System.out.println("      Full bounds: minX=" + screenBounds.getMinX() + ", maxX=" + screenBounds.getMaxX());
                        System.out.println("      Visual bounds: minX=" + visualBounds.getMinX() + " (taskbar excludes " +
                                         (visualBounds.getMinX() - screenBounds.getMinX()) + "px)");

                        // Check for ANY overlap using rectangle intersection logic
                        // Window overlaps if: windowRight > screenLeft AND windowLeft < screenRight
                        //                AND: windowBottom > screenTop AND windowTop < screenBottom
                        var windowRight = x + WIDGET_WIDTH;
                        var windowBottom = y + height;

                        var hasOverlap = (windowRight > screenBounds.getMinX()) &&     // Window extends into screen from left
                                       (x < screenBounds.getMaxX()) &&               // Window starts before screen ends
                                       (windowBottom > screenBounds.getMinY()) &&    // Window extends into screen from top
                                       (y < screenBounds.getMaxY());                 // Window starts before screen ends

                        System.out.println("      → Window rect: [" + x + "," + y + " to " + windowRight + "," + windowBottom + "]");
                        System.out.println("      → Overlap detected: " + hasOverlap);

                        if (hasOverlap) {
                            isVisibleOnAnyScreen = true;
                            System.out.println("      ✅ VALID! Window has positive projection on screen");
                            break;
                        }
                    }

                    if (!isVisibleOnAnyScreen) {
                        // Position is invalid, use center of primary screen
                        x = bounds.getMinX() + (bounds.getWidth() - WIDGET_WIDTH) / 2;
                        y = bounds.getMinY() + (bounds.getHeight() - height) / 2;
                        System.out.println("   ⚠️  Position not visible on any screen! Resetting to center: x=" + x + ", y=" + y);

                        // Save the corrected position
                        uiSettings.lastScreenLoc(String.format("%.0f,%.0f,%.0f", x, y, height));
                        saveSettings();
                    }

                    programmaticHeightChange = true;
                    System.out.println("   🎯 Setting stage position: x=" + x + ", y=" + y + ", height=" + height);
                    stage.setX(x);
                    stage.setY(y);
                    stage.setHeight(height);
                    programmaticHeightChange = false;

                    // IMPORTANT: Mark that we loaded from settings so displayUsageData won't override
                    // We set lastAccountCount to a non-negative value to prevent auto-resize
                    // The actual account count will be set when displayUsageData is called
                    if (parts.length >= 3) {
                        // User has a saved height preference - don't auto-resize
                        userManuallyResized = true;
                        System.out.println("Loaded saved window position: x=" + x + ", y=" + y + ", height=" + height);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to parse lastScreenLoc: " + e.getMessage());
            }
        }
    }

    /**
     * Save current window position and height to settings
     * Format: "x,y,height"
     */
    private void saveWindowPosition() {
        if (primaryStage != null) {
            var x = primaryStage.getX();
            var y = primaryStage.getY();
            var height = primaryStage.getHeight();
            var posString = String.format("%.0f,%.0f,%.0f", x, y, height);
            System.out.println("💾 SAVING window position: " + posString);
            uiSettings.lastScreenLoc(posString);
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

    /**
     * Enable custom vertical resize from bottom edge for transparent window
     * Detects mouse at bottom edge and allows dragging to resize downward
     */
    private void enableResizeBottom(Stage stage, VBox rootNode) {
        final double RESIZE_MARGIN = 8; // Pixels from bottom edge to detect resize
        final double[] resizeStartY = {0};
        final double[] resizeStartHeight = {0};
        final boolean[] isResizing = {false};

        rootNode.setOnMouseMoved(event -> {
            // Use screen coordinates to detect if mouse is near bottom of window
            var mouseScreenY = event.getScreenY();
            var windowBottom = stage.getY() + stage.getHeight();

            // Change cursor when near bottom edge
            if (mouseScreenY >= windowBottom - RESIZE_MARGIN) {
                rootNode.setCursor(javafx.scene.Cursor.S_RESIZE);
            } else {
                if (!isResizing[0]) {
                    rootNode.setCursor(javafx.scene.Cursor.DEFAULT);
                }
            }
        });

        rootNode.setOnMousePressed(event -> {
            // Use screen coordinates to detect if clicked near bottom of window
            var mouseScreenY = event.getScreenY();
            var windowBottom = stage.getY() + stage.getHeight();

            // Start resize if clicked near bottom edge
            if (mouseScreenY >= windowBottom - RESIZE_MARGIN) {
                isResizing[0] = true;
                resizeStartY[0] = event.getScreenY();
                resizeStartHeight[0] = stage.getHeight();
                event.consume();
            }
        });

        rootNode.setOnMouseDragged(event -> {
            if (isResizing[0]) {
                var deltaY = event.getScreenY() - resizeStartY[0];
                var newHeight = resizeStartHeight[0] + deltaY;

                // Clamp to min/max height
                newHeight = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, newHeight));

                stage.setHeight(newHeight);
                event.consume();
            }
        });

        rootNode.setOnMouseReleased(event -> {
            if (isResizing[0]) {
                userManuallyResized = true; // Mark that user manually resized
                isResizing[0] = false;
                rootNode.setCursor(javafx.scene.Cursor.DEFAULT);
                saveWindowPosition(); // Save new height
                event.consume();
            }
        });

        rootNode.setOnMouseExited(event -> {
            if (!isResizing[0]) {
                rootNode.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        });
    }


    public static void main(String[] args) {
        launch(args);
    }
}
