package xyz.jphil.ccapis.model;

import lombok.Data;
import lombok.experimental.Accessors;
import jakarta.xml.bind.annotation.*;
import xyz.jphil.ccapis.util.EnvironmentVariableResolver;

/**
 * OAuth credential model for CCAPI OAuth apps.
 * Maps to CCAPICredentialOauth tag in CCAPIsCredentials.xml
 * Shared model used by oauth module and can be loaded by base CredentialsManager.
 */
@Data
@Accessors(fluent = true, chain = true)
@XmlAccessorType(XmlAccessType.FIELD)
public class CCAPICredentialOauth {

    @XmlAttribute(name = "id", required = true)
    private String id;

    @XmlAttribute(name = "name", required = true)
    private String name;

    @XmlAttribute(name = "email", required = true)
    private String email;

    @XmlAttribute(name = "clientId", required = true)
    private String clientId;

    @XmlAttribute(name = "ccapiBaseUrl", required = true)
    private String ccapiBaseUrl;

    @XmlAttribute(name = "ccapiConsoleUrl", required = false)
    private String ccapiConsoleUrl;

    @XmlAttribute(name = "ccapiApiUrl", required = false)
    private String ccapiApiUrl;

    @XmlAttribute(name = "tier", required = false)
    private Integer tier;

    @XmlAttribute(name = "active", required = false)
    private Boolean active;

    @XmlAttribute(name = "trackUsage", required = false)
    private Boolean trackUsage;

    @XmlAttribute(name = "ping", required = false)
    private Boolean ping;

    /**
     * Get active status with default value of true if not set
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
     * Automatically adds https:// prefix if no protocol is specified.
     */
    public String resolvedCcapiBaseUrl() {
        var resolved = EnvironmentVariableResolver.resolve(ccapiBaseUrl);
        return ensureHttpsProtocol(resolved);
    }

    /**
     * Get the resolved ccapiConsoleUrl with environment variable substitution.
     * Defaults to https://console.anthropic.com if not set.
     * Automatically adds https:// prefix if no protocol is specified.
     */
    public String resolvedCcapiConsoleUrl() {
        if (ccapiConsoleUrl == null || ccapiConsoleUrl.isEmpty()) {
            return "https://console.anthropic.com";
        }
        var resolved = EnvironmentVariableResolver.resolve(ccapiConsoleUrl);
        return ensureHttpsProtocol(resolved);
    }

    /**
     * Get the resolved ccapiApiUrl with environment variable substitution.
     * Defaults to https://api.anthropic.com if not set.
     * Automatically adds https:// prefix if no protocol is specified.
     */
    public String resolvedCcapiApiUrl() {
        if (ccapiApiUrl == null || ccapiApiUrl.isEmpty()) {
            return "https://api.anthropic.com";
        }
        var resolved = EnvironmentVariableResolver.resolve(ccapiApiUrl);
        return ensureHttpsProtocol(resolved);
    }

    /**
     * Resolve CCAPI API URL with path segments.
     * Similar to NIO Paths API - joins base URL with path segments and normalizes slashes.
     *
     * Examples:
     * - resolveCcapiApiUrl("/v1/messages/count_tokens")
     * - resolveCcapiApiUrl("v1/messages/count_tokens")
     * - resolveCcapiApiUrl("/v1", "/messages", "/count_tokens")
     * - resolveCcapiApiUrl("v1", "messages", "count_tokens")
     *
     * All produce: https://api.anthropic.com/v1/messages/count_tokens
     *
     * @param pathSegments Path segments to append to base API URL
     * @return Fully resolved URL with normalized path
     */
    public String resolveCcapiApiUrl(String... pathSegments) {
        String baseUrl = resolvedCcapiApiUrl();

        if (pathSegments == null || pathSegments.length == 0) {
            return baseUrl;
        }

        // Join path segments with normalization
        return joinUrlPath(baseUrl, pathSegments);
    }

    /**
     * Resolve CCAPI Console URL with path segments.
     * Similar to NIO Paths API - joins base URL with path segments and normalizes slashes.
     *
     * @param pathSegments Path segments to append to base console URL
     * @return Fully resolved URL with normalized path
     */
    public String resolveCcapiConsoleUrl(String... pathSegments) {
        String baseUrl = resolvedCcapiConsoleUrl();

        if (pathSegments == null || pathSegments.length == 0) {
            return baseUrl;
        }

        return joinUrlPath(baseUrl, pathSegments);
    }

    /**
     * Resolve CCAPI Base URL with path segments.
     * Similar to NIO Paths API - joins base URL with path segments and normalizes slashes.
     *
     * @param pathSegments Path segments to append to base URL
     * @return Fully resolved URL with normalized path
     */
    public String resolveCcapiBaseUrl(String... pathSegments) {
        String baseUrl = resolvedCcapiBaseUrl();

        if (pathSegments == null || pathSegments.length == 0) {
            return baseUrl;
        }

        return joinUrlPath(baseUrl, pathSegments);
    }

    /**
     * Join URL base with path segments, normalizing slashes.
     * Handles leading/trailing slashes intelligently and removes double slashes.
     *
     * @param baseUrl Base URL (e.g., "https://api.anthropic.com")
     * @param pathSegments Path segments to append
     * @return Joined and normalized URL
     */
    private String joinUrlPath(String baseUrl, String... pathSegments) {
        StringBuilder result = new StringBuilder(baseUrl);

        // Ensure base URL doesn't end with slash
        if (result.charAt(result.length() - 1) == '/') {
            result.setLength(result.length() - 1);
        }

        for (String segment : pathSegments) {
            if (segment == null || segment.isEmpty()) {
                continue;
            }

            // Remove leading slashes from segment
            String cleanSegment = segment;
            while (cleanSegment.startsWith("/")) {
                cleanSegment = cleanSegment.substring(1);
            }

            // Remove trailing slashes from segment
            while (cleanSegment.endsWith("/")) {
                cleanSegment = cleanSegment.substring(0, cleanSegment.length() - 1);
            }

            if (!cleanSegment.isEmpty()) {
                result.append('/').append(cleanSegment);
            }
        }

        return result.toString();
    }

    /**
     * Ensures a URL has https:// protocol prefix.
     * If the URL already starts with http:// or https://, returns it as-is.
     * Otherwise, prepends https://
     */
    private String ensureHttpsProtocol(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "https://" + url;
    }
}
