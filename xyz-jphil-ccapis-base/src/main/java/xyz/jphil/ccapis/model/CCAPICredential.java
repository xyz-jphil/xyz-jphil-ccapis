package xyz.jphil.ccapis.model;

import lombok.Data;
import lombok.experimental.Accessors;
import jakarta.xml.bind.annotation.*;
import xyz.jphil.ccapis.util.EnvironmentVariableResolver;

/**
 * Shared credential model used across all modules.
 * Maps to CCAPICredential tag in CCAPIsCredentials.xml
 */
@Data
@Accessors(fluent = true, chain = true)
@XmlAccessorType(XmlAccessType.FIELD)
public class CCAPICredential {

    @XmlAttribute(name = "ccapiBaseUrl", required = true)
    private String ccapiBaseUrl;

    @XmlAttribute(name = "id", required = true)
    private String id;

    @XmlAttribute(name = "name", required = true)
    private String name;

    @XmlAttribute(name = "email", required = true)
    private String email;

    @XmlAttribute(name = "orgId", required = true)
    private String orgId;

    @XmlAttribute(name = "key", required = true)
    private String key;

    @XmlAttribute(name = "active", required = false)
    private Boolean active;

    @XmlAttribute(name = "trackUsage", required = false)
    private Boolean trackUsage;

    @XmlAttribute(name = "ping", required = false)
    private Boolean ping;

    @XmlAttribute(name = "tier", required = false)
    private Integer tier;

    @XmlAttribute(name = "ua", required = false)
    private String ua;

    /**
     * Get active with default value of true if not set
     * (credentials are active by default for backwards compatibility)
     */
    public boolean active() {
        return active != null ? active : true;
    }

    /**
     * Get trackUsage with default value of false if not set
     */
    public boolean trackUsage() {
        return trackUsage != null ? trackUsage : false;
    }

    /**
     * Get ping with default value of false if not set
     */
    public boolean ping() {
        return ping != null ? ping : false;
    }

    /**
     * Get tier with default value of 1 if not set
     */
    public int tier() {
        return tier != null ? tier : 1;
    }

    /**
     * Get the resolved ccapiBaseUrl with environment variable substitution.
     * Supports Windows-style environment variables like %CCAPI_BASE_URL%
     *
     * @return the ccapiBaseUrl with environment variables resolved
     */
    public String resolvedCcapiBaseUrl() {
        return EnvironmentVariableResolver.resolve(ccapiBaseUrl);
    }
}
