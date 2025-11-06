package xyz.jphil.ccapis.usage_tracker.model;

import lombok.Data;
import lombok.experimental.Accessors;
import xyz.jphil.ccapis.model.CCAPICredential;
import xyz.jphil.ccapis.model.Usage;
import xyz.jphil.ccapis.model.UsageData;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Account usage with calculated metrics for display.
 * Now uses shared CCAPICredential from base module.
 */
@Data
@Accessors(fluent = true, chain = true)
public class AccountUsage {

    private CCAPICredential credential;
    private UsageData usageData;
    private OffsetDateTime fetchedAt;

    public AccountUsage(CCAPICredential credential, UsageData usageData) {
        this.credential = credential;
        this.usageData = usageData;
        this.fetchedAt = OffsetDateTime.now();
    }

    /**
     * Get 5-hour usage percentage
     */
    public double getFiveHourUsagePercent() {
        if (usageData.fiveHour() == null) {
            return 0.0;
        }
        return usageData.fiveHour().utilization();
    }

    /**
     * Get time elapsed percentage for 5-hour window
     */
    public double getFiveHourTimeElapsedPercent() {
        var fiveHour = usageData.fiveHour();
        if (fiveHour == null || fiveHour.resetsAt() == null) {
            return 0.0;
        }

        var now = OffsetDateTime.now();
        var resetTime = fiveHour.resetsAt();
        var fiveHoursInSeconds = 5 * 60 * 60;

        // Calculate elapsed time
        var timeUntilReset = Duration.between(now, resetTime).getSeconds();
        var elapsedSeconds = fiveHoursInSeconds - timeUntilReset;

        return (elapsedSeconds * 100.0) / fiveHoursInSeconds;
    }

    /**
     * Get ratio comparing usage to time elapsed
     * > 1.0 means using more than proportional to time
     * < 1.0 means using less than proportional to time
     * = 1.0 means usage matches time proportionally
     */
    public double getUsageToTimeRatio() {
        var timePercent = getFiveHourTimeElapsedPercent();
        if (timePercent == 0.0) {
            return 0.0;
        }
        return getFiveHourUsagePercent() / timePercent;
    }

    /**
     * Get formatted status string for CLI display
     */
    public String getFormattedStatus() {
        var id = credential.id();
        var fiveHourUsage = getFiveHourUsagePercent();
        var timeElapsed = getFiveHourTimeElapsedPercent();
        var ratio = getUsageToTimeRatio();

        return String.format("%s | 5hr: %5.1f%% | Time: %5.1f%% | Ratio: %.2f",
                id, fiveHourUsage, timeElapsed, ratio);
    }

    /**
     * Get time remaining until reset
     */
    public String getTimeUntilReset() {
        var fiveHour = usageData.fiveHour();
        if (fiveHour == null || fiveHour.resetsAt() == null) {
            return "N/A";
        }

        var duration = Duration.between(OffsetDateTime.now(), fiveHour.resetsAt());
        var hours = duration.toHours();
        var minutes = duration.toMinutesPart();

        return String.format("%dh %dm", hours, minutes);
    }

    /**
     * Get cycle reset time in HH:mm format (system local time, rounded to nearest 30-min boundary)
     */
    public String getCycleResetTime() {
        var fiveHour = usageData.fiveHour();
        if (fiveHour == null || fiveHour.resetsAt() == null) {
            return "N/A";
        }

        // Convert UTC to system local time zone
        var resetTimeUtc = fiveHour.resetsAt();
        var resetTimeLocal = resetTimeUtc.atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime();

        // Round to nearest 30-minute boundary: add 15 minutes, then round down to 30-min intervals
        var totalMinutes = resetTimeLocal.getHour() * 60 + resetTimeLocal.getMinute();
        var roundedTotalMinutes = ((totalMinutes + 15) / 30) * 30;
        var roundedHour = (roundedTotalMinutes / 60) % 24;
        var roundedMinute = roundedTotalMinutes % 60;

        return String.format("%02d:%02d", roundedHour, roundedMinute);
    }
}
