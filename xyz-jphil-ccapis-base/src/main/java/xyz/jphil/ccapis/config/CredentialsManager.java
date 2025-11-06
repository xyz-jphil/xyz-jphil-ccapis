package xyz.jphil.ccapis.config;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import xyz.jphil.ccapis.model.CCAPIsCredentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manager for loading and saving shared credentials file.
 * All modules (impl, usage_tracker, ping) use this to access credentials.
 *
 * Location: <userhome>/xyz-jphil/ccapis/CCAPIsCredentials.xml
 */
public class CredentialsManager {

    private static final String DEFAULT_CREDENTIALS_DIR = "xyz-jphil/ccapis";
    private static final String CREDENTIALS_FILENAME = "CCAPIsCredentials.xml";

    private final Path credentialsPath;

    /**
     * Create manager with default credentials file location
     */
    public CredentialsManager() {
        this.credentialsPath = getDefaultCredentialsPath();
    }

    /**
     * Create manager with custom credentials file path
     */
    public CredentialsManager(String customPath) {
        this.credentialsPath = Paths.get(customPath);
    }

    /**
     * Create manager with custom Path
     */
    public CredentialsManager(Path customPath) {
        this.credentialsPath = customPath;
    }

    /**
     * Get the default credentials file path
     */
    public static Path getDefaultCredentialsPath() {
        var userHome = System.getProperty("user.home");
        return Paths.get(userHome, DEFAULT_CREDENTIALS_DIR, CREDENTIALS_FILENAME);
    }

    /**
     * Load credentials from XML file
     */
    public CCAPIsCredentials load() throws JAXBException, IOException {
        if (!Files.exists(credentialsPath)) {
            throw new IOException("Credentials file not found: " + credentialsPath +
                "\nPlease create the CCAPIsCredentials.xml file with your credentials.");
        }

        var jaxbContext = JAXBContext.newInstance(CCAPIsCredentials.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        return (CCAPIsCredentials) unmarshaller.unmarshal(credentialsPath.toFile());
    }

    /**
     * Save credentials to XML file
     */
    public void save(CCAPIsCredentials credentials) throws JAXBException, IOException {
        // Ensure directory exists
        var directory = credentialsPath.getParent();
        if (directory != null && !Files.exists(directory)) {
            Files.createDirectories(directory);
        }

        var jaxbContext = JAXBContext.newInstance(CCAPIsCredentials.class);
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(credentials, credentialsPath.toFile());
    }

    /**
     * Get the path to the credentials file
     */
    public Path getCredentialsPath() {
        return credentialsPath;
    }

    /**
     * Check if credentials file exists
     */
    public boolean credentialsFileExists() {
        return Files.exists(credentialsPath);
    }
}
