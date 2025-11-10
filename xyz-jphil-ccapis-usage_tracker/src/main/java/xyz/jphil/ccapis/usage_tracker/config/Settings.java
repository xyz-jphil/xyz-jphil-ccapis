package xyz.jphil.ccapis.usage_tracker.config;

import lombok.Data;
import lombok.experimental.Accessors;

import jakarta.xml.bind.annotation.*;

/**
 * Settings model for usage tracker configuration.
 * Now only contains UI settings. Credentials are loaded from shared CCAPIsCredentials.xml.
 * Mapped to CCAPIsUsageTrackerSettings.xml in user home directory.
 */
@Data
@Accessors(fluent = true, chain = true)
@XmlRootElement(name = "CCAPIsUsageTrackerSettings")
@XmlAccessorType(XmlAccessType.FIELD)
public class Settings {

    @XmlElement(name = "ui")
    private UISettings ui;

    /**
     * UI settings for widget appearance and behavior
     */
    @Data
    @Accessors(fluent = true, chain = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class UISettings {

        @XmlAttribute(name = "layout")
        private String layout = "horizontal"; // horizontal or vertical (future)

        @XmlAttribute(name = "showTime")
        private String showTime = "timeRemaining"; // timeRemaining or resetTime

        @XmlAttribute(name = "idMaxLen")
        private int idMaxLen = 10; // Maximum length of account ID to display

        @XmlAttribute(name = "lastScreenLoc")
        private String lastScreenLoc; // Format: "x,y,height" - last screen position and height for window (auto-saved)

        @XmlAttribute(name = "usageRefreshIntervalSecs", required = false)
        private Integer usageRefreshIntervalSecs; // Refresh interval in seconds (default: 60)

        @XmlAttribute(name = "pingIntervalDurSec", required = false)
        private Integer pingIntervalDurSec; // Ping interval duration in seconds

        @XmlAttribute(name = "pingMessageVisible", required = false)
        private Boolean pingMessageVisible; // Whether ping messages should be visible (default: true)

        // Return usageRefreshIntervalSecs with default value of 60 if not set or invalid
        public int usageRefreshIntervalSecs() {
            if (usageRefreshIntervalSecs != null && usageRefreshIntervalSecs > 0) {
                return usageRefreshIntervalSecs;
            }
            return 60; // Default: 60 seconds
        }

        // Return pingIntervalDurSec with default value of 3600 (1 hour) if not set or invalid
        public int pingIntervalDurSec() {
            if (pingIntervalDurSec != null && pingIntervalDurSec > 0) {
                return pingIntervalDurSec;
            }
            return 3600; // Default: 1 hour
        }

        // Return pingMessageVisible with default value of true if not set
        public boolean pingMessageVisible() {
            return pingMessageVisible != null ? pingMessageVisible : true;
        }
    }

    /**
     * Get UI settings with defaults if not configured
     */
    public UISettings getUiSettings() {
        if (ui == null) {
            ui = new UISettings();
        }
        return ui;
    }
}
