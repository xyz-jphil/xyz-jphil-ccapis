package xyz.jphil.ccapis.model;

import lombok.Data;
import lombok.experimental.Accessors;
import jakarta.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Root wrapper for shared credentials file.
 * Maps to CCAPIsCredentials.xml in <userhome>/xyz-jphil/ccapis/
 *
 * Supports both regular session-based credentials (CCAPICredential)
 * and OAuth credentials (CCAPICredentialOauth).
 */
@Data
@Accessors(fluent = true, chain = true)
@XmlRootElement(name = "CCAPIsCredentials")
@XmlAccessorType(XmlAccessType.FIELD)
public class CCAPIsCredentials {

    @XmlElement(name = "CircuitBreakerConfig")
    private CircuitBreakerConfig circuitBreakerConfig;

    @XmlElement(name = "CCAPICredential")
    private List<CCAPICredential> credentials = new ArrayList<>();

    @XmlElement(name = "CCAPICredentialOauth")
    private List<CCAPICredentialOauth> oauthCredentials = new ArrayList<>();

    /**
     * Get all active credentials
     */
    public List<CCAPICredential> getActiveCredentials() {
        return credentials.stream()
                .filter(CCAPICredential::active)
                .toList();
    }

    /**
     * Get all credentials with usage tracking enabled
     */
    public List<CCAPICredential> getTrackUsageCredentials() {
        return credentials.stream()
                .filter(CCAPICredential::trackUsage)
                .toList();
    }

    /**
     * Get all credentials with ping enabled
     */
    public List<CCAPICredential> getPingEnabledCredentials() {
        return credentials.stream()
                .filter(CCAPICredential::ping)
                .toList();
    }

    /**
     * Get credential by ID
     */
    public CCAPICredential getById(String id) {
        return credentials.stream()
                .filter(c -> id.equals(c.id()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all active OAuth credentials
     */
    public List<CCAPICredentialOauth> getActiveOauthCredentials() {
        return oauthCredentials.stream()
                .filter(CCAPICredentialOauth::active)
                .toList();
    }

    /**
     * Get all OAuth credentials with usage tracking enabled
     */
    public List<CCAPICredentialOauth> getTrackUsageOauthCredentials() {
        return oauthCredentials.stream()
                .filter(CCAPICredentialOauth::trackUsage)
                .toList();
    }

    /**
     * Get all OAuth credentials with ping enabled
     */
    public List<CCAPICredentialOauth> getPingEnabledOauthCredentials() {
        return oauthCredentials.stream()
                .filter(CCAPICredentialOauth::ping)
                .toList();
    }

    /**
     * Get OAuth credential by ID
     */
    public CCAPICredentialOauth getOauthById(String id) {
        return oauthCredentials.stream()
                .filter(c -> id.equals(c.id()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get circuit breaker configuration with fallback to defaults
     */
    public CircuitBreakerConfig getCircuitBreakerConfig() {
        return circuitBreakerConfig != null ? circuitBreakerConfig : new CircuitBreakerConfig();
    }
}
