package xyz.jphil.ccapis.oauth;

import xyz.jphil.ccapis.config.CredentialsManager;
import xyz.jphil.ccapis.model.CCAPIsCredentials;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Manager for loading OAuth credentials from shared credentials file.
 * Delegates to base CredentialsManager and provides convenience methods for OAuth.
 * Location: <userhome>/xyz-jphil/ccapis/CCAPIsCredentials.xml
 */
public class OAuthCredentialsManager {

    private final CredentialsManager baseManager;

    /**
     * Create manager with default credentials file location
     */
    public OAuthCredentialsManager() {
        this.baseManager = new CredentialsManager();
    }

    /**
     * Create manager with custom credentials file path
     */
    public OAuthCredentialsManager(String customPath) {
        this.baseManager = new CredentialsManager(customPath);
    }

    /**
     * Create manager with custom Path
     */
    public OAuthCredentialsManager(Path customPath) {
        this.baseManager = new CredentialsManager(customPath);
    }

    /**
     * Load all credentials (including OAuth credentials) from XML file
     */
    public CCAPIsCredentials load() throws JAXBException, IOException {
        return baseManager.load();
    }

    /**
     * Get the path to the credentials file
     */
    public Path getCredentialsPath() {
        return baseManager.getCredentialsPath();
    }

    /**
     * Check if credentials file exists
     */
    public boolean credentialsFileExists() {
        return baseManager.credentialsFileExists();
    }
}
