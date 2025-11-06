package xyz.jphil.ccapis.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages persistence of OAuth tokens to local filesystem.
 * Tokens are stored in JSON format alongside CCAPIsCredentials.xml
 * Location: <userhome>/xyz-jphil/ccapis/oauth-tokens/
 */
public class OAuthTokenStorage {

    private static final String DEFAULT_STORAGE_DIR = "xyz-jphil/ccapis/oauth-tokens";
    private static final String TOKEN_FILE_SUFFIX = ".tokens.json";

    private final Path storageDir;
    private final ObjectMapper objectMapper;

    /**
     * Create storage manager with default location
     */
    public OAuthTokenStorage() {
        this(getDefaultStorageDir());
    }

    /**
     * Create storage manager with custom directory
     */
    public OAuthTokenStorage(Path storageDir) {
        this.storageDir = storageDir;
        this.objectMapper = createObjectMapper();
    }

    private static Path getDefaultStorageDir() {
        var userHome = System.getProperty("user.home");
        return Paths.get(userHome, DEFAULT_STORAGE_DIR);
    }

    private ObjectMapper createObjectMapper() {
        var mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );
        return mapper;
    }

    /**
     * Save OAuth tokens for a credential
     *
     * @param credentialId credential ID
     * @param tokens OAuth tokens to save
     * @throws IOException if save fails
     */
    public void save(String credentialId, OAuthTokens tokens) throws IOException {
        ensureStorageDirExists();

        var tokenFile = getTokenFilePath(credentialId);
        objectMapper.writeValue(tokenFile.toFile(), tokens);
    }

    /**
     * Load OAuth tokens for a credential
     *
     * @param credentialId credential ID
     * @return OAuth tokens or null if not found
     * @throws IOException if load fails
     */
    public OAuthTokens load(String credentialId) throws IOException {
        var tokenFile = getTokenFilePath(credentialId);

        if (!Files.exists(tokenFile)) {
            return null;
        }

        return objectMapper.readValue(tokenFile.toFile(), OAuthTokens.class);
    }

    /**
     * Delete OAuth tokens for a credential
     *
     * @param credentialId credential ID
     * @throws IOException if delete fails
     */
    public void delete(String credentialId) throws IOException {
        var tokenFile = getTokenFilePath(credentialId);

        if (Files.exists(tokenFile)) {
            Files.delete(tokenFile);
        }
    }

    /**
     * Check if tokens exist for a credential
     *
     * @param credentialId credential ID
     * @return true if tokens exist
     */
    public boolean exists(String credentialId) {
        var tokenFile = getTokenFilePath(credentialId);
        return Files.exists(tokenFile);
    }

    /**
     * Get the file path for a credential's tokens
     */
    private Path getTokenFilePath(String credentialId) {
        var sanitizedId = credentialId.replaceAll("[^a-zA-Z0-9_-]", "_");
        var fileName = sanitizedId + TOKEN_FILE_SUFFIX;
        return storageDir.resolve(fileName);
    }

    /**
     * Ensure storage directory exists
     */
    private void ensureStorageDirExists() throws IOException {
        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
        }
    }

    /**
     * Get the storage directory path
     */
    public Path getStorageDir() {
        return storageDir;
    }
}
