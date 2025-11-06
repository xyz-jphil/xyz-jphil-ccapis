package xyz.jphil.ccapis.config;

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
 * Manages loading and saving of CCAPIsSettings.xml
 */
public class SettingsManager {

    private final Path settingsPath;

    public SettingsManager() {
        var userHome = System.getProperty("user.home");
        var settingsDir = Paths.get(userHome, "xyz-jphil/ccapis");
        this.settingsPath = settingsDir.resolve("CCAPIsSettings.xml");

        // Create directory if it doesn't exist
        try {
            Files.createDirectories(settingsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create settings directory: " + settingsDir, e);
        }
    }

    /**
     * Custom constructor for alternative paths
     */
    public SettingsManager(Path settingsPath) {
        this.settingsPath = settingsPath;
        try {
            if (settingsPath.getParent() != null) {
                Files.createDirectories(settingsPath.getParent());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create settings directory: " + settingsPath.getParent(), e);
        }
    }

    /**
     * Custom constructor with string path
     */
    public SettingsManager(String settingsPath) {
        this(Paths.get(settingsPath));
    }

    /**
     * Load settings from XML file
     */
    public Settings load() throws JAXBException, IOException {
        if (!Files.exists(settingsPath)) {
            throw new IOException("Settings file not found: " + settingsPath +
                "\nPlease create a CCAPIsSettings.xml file with your CCAPI credentials.");
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
     * Create an empty settings template
     */
    public Settings createEmptyTemplate() {
        var settings = new Settings();
        settings.ccapiBaseUrl("%CCAPI_BASE_URL%");

        // Add example credential entry
        var credential = new Settings.Credential()
                .id("EXAMPLE")
                .name("Example Account")
                .email("example@example.com")
                .orgId("your-org-id-here")
                .key("sk-ant-sid01-...")
                .active(false)
                .tier(1)
                .ua("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36");

        settings.credentials().add(credential);

        return settings;
    }

    /**
     * Get the settings file path
     */
    public Path getSettingsPath() {
        return settingsPath;
    }
}
