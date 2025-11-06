package xyz.jphil.ccapis.usage_tracker.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Custom compact progress indicator using Rectangle shapes.
 * Shows percentage text above a colored progress bar.
 * Used for both usage and time progress indicators with same width for visual comparison.
 */
public class ProgressIndicatorComponent extends VBox {

    private static final double PROGRESS_BAR_WIDTH = 38;
    private static final double PROGRESS_BAR_HEIGHT = 2;
    private static final int PERCENTAGE_FONT_SIZE = 7;

    private final Label percentageLabel;
    private final Rectangle backgroundRect;
    private final Rectangle fillRect;

    /**
     * Creates a new progress indicator component.
     *
     * @param value Current value
     * @param max Maximum value
     * @param color CSS color for the progress bar (e.g., "#44FF44", "#FFAA00", "#FF4444")
     */
    public ProgressIndicatorComponent(double value, double max, String color) {
        // Calculate percentage
        var percentage = max > 0 ? (value / max) * 100.0 : 0.0;
        var progress = Math.min(percentage / 100.0, 1.0);

        // Percentage label
        percentageLabel = new Label(String.format("%.0f%%", percentage));
        percentageLabel.setStyle(String.format(
            "-fx-text-fill: white; -fx-font-size: %dpx; -fx-font-weight: bold;",
            PERCENTAGE_FONT_SIZE
        ));
        percentageLabel.setAlignment(Pos.CENTER);
        percentageLabel.setMaxWidth(PROGRESS_BAR_WIDTH);

        // Background rectangle (dark grey)
        backgroundRect = new Rectangle(PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT);
        backgroundRect.setFill(Color.web("#333333"));

        // Fill rectangle (colored, will be clipped to progress)
        fillRect = new Rectangle(PROGRESS_BAR_WIDTH * progress, PROGRESS_BAR_HEIGHT);
        fillRect.setFill(Color.web(color));

        // Stack the rectangles
        var progressContainer = new StackPane();
        progressContainer.setAlignment(Pos.CENTER_LEFT);
        progressContainer.getChildren().addAll(backgroundRect, fillRect);

        // Layout configuration - no spacing for tight vertical layout
        setSpacing(0);
        setPadding(Insets.EMPTY);
        setAlignment(Pos.CENTER);

        getChildren().addAll(percentageLabel, progressContainer);
    }

    /**
     * Updates the progress indicator with new values.
     *
     * @param value Current value
     * @param max Maximum value
     */
    public void update(double value, double max) {
        var percentage = max > 0 ? (value / max) * 100.0 : 0.0;
        var progress = Math.min(percentage / 100.0, 1.0);

        percentageLabel.setText(String.format("%.0f%%", percentage));
        fillRect.setWidth(PROGRESS_BAR_WIDTH * progress);

        // Update color based on progress thresholds
        var newColor = getProgressColor(progress);
        fillRect.setFill(Color.web(newColor));
    }

    /**
     * Gets color based on progress percentage.
     *
     * @param progress Progress value (0.0 to 1.0)
     * @return CSS color string
     */
    private String getProgressColor(double progress) {
        if (progress >= 0.8) return "#FF4444"; // Red
        if (progress >= 0.6) return "#FFAA00"; // Orange
        return "#44FF44"; // Green
    }
}
