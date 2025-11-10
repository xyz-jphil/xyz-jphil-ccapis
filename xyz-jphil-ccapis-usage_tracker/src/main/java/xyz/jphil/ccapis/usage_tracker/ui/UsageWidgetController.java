package xyz.jphil.ccapis.usage_tracker.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;
import lombok.Setter;
import xyz.jphil.ccapis.usage_tracker.model.AccountUsage;

/**
 * Controller for the FXML-based usage widget UI.
 * Manages the compact UI components for displaying account usage metrics.
 */
public class UsageWidgetController {

    // Root container (for background color change)
    private HBox rootContainer;

    // Active status checkbox
    @FXML
    private Label activeCheckbox;

    // Account information labels
    @FXML
    private Label accName;

    @FXML
    private Label accType;

    // Interactive button labels
    @FXML
    private Label pingButton;

    @FXML
    private Label refreshButton;

    // Usage percentage labels
    @FXML
    private Label percentUsage;

    @FXML
    private Label percentTime;

    // Progress bar rectangles
    @FXML
    private Rectangle usageProgress;

    @FXML
    private Rectangle timeProgress;

    // Metric labels
    @FXML
    private Label timeUsageRatioAsPercent;

    @FXML
    private Label cycleEndTime;

    // Callback handlers (set by parent)
    @Setter
    private Runnable onRefresh;

    @Setter
    private Runnable onPing;

    /**
     * Set the root container (called from UsageWidget after FXML load)
     */
    public void setRootContainer(HBox root) {
        this.rootContainer = root;
    }

    /**
     * Initialize the controller after FXML loading.
     * Sets up event handlers for the interactive elements.
     */
    @FXML
    public void initialize() {
        // Root container will be set by parent after loading

        // Setup click handlers for button labels - ensure UI runs on JavaFX thread
        if (refreshButton != null) {
            refreshButton.setOnMouseClicked(event -> {
                if (onRefresh != null) {
                    // Run callback on background thread to avoid blocking UI
                    new Thread(() -> {
                        try {
                            onRefresh.run();
                        } catch (Exception e) {
                            System.err.println("Error in refresh callback: " + e.getMessage());
                        }
                    }).start();
                }
            });
            // Add hover effect
            refreshButton.setOnMouseEntered(event ->
                refreshButton.setStyle(refreshButton.getStyle() + "-fx-opacity: 0.7;"));
            refreshButton.setOnMouseExited(event ->
                refreshButton.setStyle(refreshButton.getStyle().replace("-fx-opacity: 0.7;", "")));
        }

        if (pingButton != null) {
            pingButton.setOnMouseClicked(event -> {
                if (onPing != null) {
                    // Run callback on background thread to avoid blocking UI
                    new Thread(() -> {
                        try {
                            onPing.run();
                        } catch (Exception e) {
                            System.err.println("Error in ping callback: " + e.getMessage());
                        }
                    }).start();
                }
            });
            // Add hover effect
            pingButton.setOnMouseEntered(event ->
                pingButton.setStyle(pingButton.getStyle() + "-fx-opacity: 0.7;"));
            pingButton.setOnMouseExited(event ->
                pingButton.setStyle(pingButton.getStyle().replace("-fx-opacity: 0.7;", "")));
        }
    }

    /**
     * Update the UI with account usage data.
     * Must be called on JavaFX Application Thread.
     *
     * @param accountUsage The account usage data to display
     * @param showTimeFormat The time format to show ("resetTime" or "timeRemaining")
     */
    public void updateUsageData(AccountUsage accountUsage, String showTimeFormat) {
        // Ensure we're on the JavaFX thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> updateUsageData(accountUsage, showTimeFormat));
            return;
        }

        // Update active status checkbox
        var isActive = accountUsage.credential().active();
        if (activeCheckbox != null) {
            activeCheckbox.setText(isActive ? "✔" : "✘");
            activeCheckbox.setStyle("-fx-text-fill: " + (isActive ? "#44FF44" : "#FF4444") + "; -fx-font-size: 8px;");
        }

        // Update background color based on active status
        if (rootContainer != null) {
            var bgColor = isActive ? "#2A2A2A" : "#3A2A2A"; // Slightly lighter for inactive
            rootContainer.setStyle("-fx-background-color: " + bgColor + ";");
        }

        // Update account name (truncate if needed)
        var fullId = accountUsage.credential().id();
        var displayId = fullId.length() > 3 ? fullId.substring(0, 3) : fullId;
        accName.setText(displayId.toUpperCase());

        // Update account type (F for free, or tier number)
        var tier = accountUsage.credential().tier();
        accType.setText(tier == 0 ? "F" : String.valueOf(tier));

        // Update usage percentage
        var usagePercent = accountUsage.getFiveHourUsagePercent();
        percentUsage.setText(String.format("%.0f%%", usagePercent));

        // Update time percentage
        var timePercent = accountUsage.getFiveHourTimeElapsedPercent();
        percentTime.setText(String.format("%.0f%%", timePercent));

        // Update progress bar widths (max width is 40 based on FXML)
        var maxWidth = 40.0;
        usageProgress.setWidth((usagePercent / 100.0) * maxWidth);
        timeProgress.setWidth((timePercent / 100.0) * maxWidth);

        // Update progress bar colors based on usage percentage
        var usageColor = getProgressColorFromPercent(usagePercent);
        usageProgress.setFill(javafx.scene.paint.Color.web(usageColor));
        percentUsage.setStyle("-fx-text-fill: " + usageColor + "; -fx-font-size: 7px; -fx-font-weight: bold;");

        // Update utilization rate
        var utilizationRate = accountUsage.getUsageToTimeRatio() * 100.0;
        timeUsageRatioAsPercent.setText(String.format("%.0f%%", utilizationRate));

        // Update utilization color
        var rateColor = getUtilizationColor(utilizationRate);
        timeUsageRatioAsPercent.setStyle("-fx-text-fill: " + rateColor + "; -fx-font-size: 8px; -fx-font-weight: bold;");

        // Update time info based on format setting
        var timeText = "resetTime".equals(showTimeFormat)
            ? accountUsage.getCycleResetTime()
            : accountUsage.getTimeUntilReset();
        cycleEndTime.setText(timeText);
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

    /**
     * Set the refresh button visibility.
     */
    public void setRefreshButtonVisible(boolean visible) {
        if (refreshButton != null) {
            refreshButton.setVisible(visible);
        }
    }

    /**
     * Set the ping button visibility.
     */
    public void setPingButtonVisible(boolean visible) {
        if (pingButton != null) {
            pingButton.setVisible(visible);
        }
    }

    /**
     * Set the refresh button busy state (red color, disabled).
     * Call on JavaFX thread.
     */
    public void setRefreshButtonBusy(boolean busy) {
        if (refreshButton == null) return;

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setRefreshButtonBusy(busy));
            return;
        }

        if (busy) {
            // Make red and disable mouse events
            refreshButton.setStyle("-fx-text-fill: #FF4444; -fx-cursor: default; -fx-opacity: 0.7;");
            refreshButton.setDisable(true);
        } else {
            // Restore normal state
            refreshButton.setStyle("-fx-text-fill: white; -fx-cursor: hand;");
            refreshButton.setDisable(false);
        }
    }

    /**
     * Set the ping button busy state (red color, disabled).
     * Call on JavaFX thread.
     */
    public void setPingButtonBusy(boolean busy) {
        if (pingButton == null) return;

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setPingButtonBusy(busy));
            return;
        }

        if (busy) {
            // Make red and disable mouse events
            pingButton.setStyle("-fx-text-fill: #FF4444; -fx-cursor: default; -fx-opacity: 0.7;");
            pingButton.setDisable(true);
        } else {
            // Restore normal state
            pingButton.setStyle("-fx-text-fill: white; -fx-cursor: hand;");
            pingButton.setDisable(false);
        }
    }
}
