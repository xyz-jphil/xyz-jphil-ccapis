package xyz.jphil.ccapis.proxy;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import xyz.jphil.ccapis.config.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages loading and saving of CCAPIProxySettings.xml
 *
 * <p>Handles proxy-specific settings including:
 * <ul>
 *   <li>Debug logging configuration</li>
 *   <li>Conversation visibility settings</li>
 *   <li>Output limits and file logging</li>
 * </ul>
 */
public class ProxySettingsManager {

    private final Path settingsPath;

    /**
     * Create settings manager with default location
     * Default: <userhome>/xyz-jphil/ccapis/CCAPIProxySettings.xml
     */
    public ProxySettingsManager() {
        var userHome = System.getProperty("user.home");
        var settingsDir = Paths.get(userHome, "xyz-jphil", "ccapis");
        this.settingsPath = settingsDir.resolve("CCAPIProxySettings.xml");

        // Create directory if it doesn't exist
        try {
            Files.createDirectories(settingsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create settings directory: " + settingsDir, e);
        }
    }

    /**
     * Create settings manager with custom path
     */
    public ProxySettingsManager(Path settingsPath) {
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
     * Create settings manager with custom path (string)
     */
    public ProxySettingsManager(String settingsPath) {
        this(Paths.get(settingsPath));
    }

    /**
     * Load proxy settings from XML file
     * Returns null if file doesn't exist (graceful degradation)
     */
    public Settings.ProxySettings load() {
        if (!Files.exists(settingsPath)) {
            System.out.println("[PROXY] Settings file not found: " + settingsPath);
            System.out.println("[PROXY] Debug logging will be disabled. Create CCAPIProxySettings.xml to enable.");
            return null;
        }

        try {
            var jaxbContext = JAXBContext.newInstance(Settings.ProxySettings.class);
            var unmarshaller = jaxbContext.createUnmarshaller();
            return (Settings.ProxySettings) unmarshaller.unmarshal(settingsPath.toFile());
        } catch (JAXBException e) {
            System.err.println("[PROXY] Failed to load settings from: " + settingsPath);
            System.err.println("[PROXY] Error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("[PROXY] Debug logging will be disabled.");
            return null;
        }
    }

    /**
     * Save proxy settings to XML file
     */
    public void save(Settings.ProxySettings settings) throws JAXBException {
        var jaxbContext = JAXBContext.newInstance(Settings.ProxySettings.class);
        var marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(settings, settingsPath.toFile());
    }

    /**
     * Get the settings file path
     */
    public Path getSettingsPath() {
        return settingsPath;
    }

    /**
     * Check if settings file exists
     */
    public boolean exists() {
        return Files.exists(settingsPath);
    }
}
