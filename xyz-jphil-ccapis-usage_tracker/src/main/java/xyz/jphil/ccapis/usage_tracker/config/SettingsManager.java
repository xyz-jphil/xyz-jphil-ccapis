package xyz.jphil.ccapis.usage_tracker.config;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages loading and saving of CCAPIsUsageTrackerSettings.xml
 * Default location: <userhome>/xyz-jphil/ccapis/usage_tracker/CCAPIsUsageTrackerSettings.xml
 */
public class SettingsManager {

    private static final String SETTINGS_DIR = "xyz-jphil/ccapis/usage_tracker";
    private static final String SETTINGS_FILE = "CCAPIsUsageTrackerSettings.xml";

    private final Path settingsPath;

    public SettingsManager() {
        var userHome = System.getProperty("user.home");
        var settingsDir = Paths.get(userHome, SETTINGS_DIR);
        this.settingsPath = settingsDir.resolve(SETTINGS_FILE);

        // Create directory if it doesn't exist
        try {
            Files.createDirectories(settingsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create settings directory: " + settingsDir, e);
        }
    }

    /**
     * Custom constructor for testing or alternative paths
     */
    public SettingsManager(Path settingsPath) {
        this.settingsPath = settingsPath;
        try {
            Files.createDirectories(settingsPath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create settings directory: " + settingsPath.getParent(), e);
        }
    }

    /**
     * Load settings from XML file
     */
    public Settings load() throws JAXBException, IOException {
        if (!Files.exists(settingsPath)) {
            // Create empty template if doesn't exist
            var settings = createEmptyTemplate();
            save(settings);
            return settings;
        }

        var jaxbContext = JAXBContext.newInstance(Settings.class);
        var unmarshaller = jaxbContext.createUnmarshaller();
        return (Settings) unmarshaller.unmarshal(settingsPath.toFile());
    }

    /**
     * Save settings to XML file
     */
    public void save(Settings settings) throws JAXBException {
        var jaxbContext = JAXBContext.newInstance(Settings.class);
        var marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(settings, settingsPath.toFile());
    }

    /**
     * Create an empty settings template with UI defaults
     */
    public Settings createEmptyTemplate() {
        var settings = new Settings();
        var ui = new Settings.UISettings()
                .layout("horizontal")
                .showTime("timeRemaining")
                .idMaxLen(10)
                .usageRefreshIntervalSecs(60)
                .pingIntervalDurSec(3600)
                .pingMessageVisible(true);

        settings.ui(ui);

        return settings;
    }

    /**
     * Get the settings file path
     */
    public Path getSettingsPath() {
        return settingsPath;
    }
}
