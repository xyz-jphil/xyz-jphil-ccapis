package xyz.jphil.ccapis.config;

import lombok.Data;
import lombok.experimental.Accessors;

import jakarta.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings model for CCAPI client configuration
 * Mapped to CCAPIsSettings.xml
 */
@Data
@Accessors(fluent = true, chain = true)
@XmlRootElement(name = "CCAPIsSettings")
@XmlAccessorType(XmlAccessType.FIELD)
public class Settings {

    @XmlElement(name = "ccapiBaseUrl")
    private String ccapiBaseUrl;

    @XmlElementWrapper(name = "credentials")
    @XmlElement(name = "credential")
    private List<Credential> credentials = new ArrayList<>();

    @XmlElement(name = "CCAPIProxySettings")
    private ProxySettings proxySettings;

    /**
     * CCAPI Proxy debug and configuration settings
     */
    @Data
    @Accessors(fluent = true, chain = true)
    @XmlRootElement(name = "CCAPIProxySettings")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ProxySettings {

        @XmlElement(name = "conversationDebug")
        private ConversationDebug conversationDebug;

        @XmlElement(name = "ccapi")
        private CcapiSettings ccapi;

        /**
         * Conversation debug settings
         */
        @Data
        @Accessors(fluent = true, chain = true)
        @XmlAccessorType(XmlAccessType.FIELD)
        public static class ConversationDebug {
            @XmlAttribute(name = "enabled")
            private boolean enabled;

            @XmlAttribute(name = "stdOutLimit")
            private Integer stdOutLimit;

            @XmlAttribute(name = "showInput")
            private boolean showInput;

            @XmlAttribute(name = "showOutput")
            private boolean showOutput;

            @XmlElement(name = "outputToFile")
            private OutputToFile outputToFile;

            @Data
            @Accessors(fluent = true, chain = true)
            @XmlAccessorType(XmlAccessType.FIELD)
            public static class OutputToFile {
                @XmlAttribute(name = "enabled")
                private boolean enabled;

                @XmlAttribute(name = "clearPreviousAtStart")
                private boolean clearPreviousAtStart;

                @XmlAttribute(name = "saveAsIndividualFiles")
                private boolean saveAsIndividualFiles;

                @XmlAttribute(name = "createIndexFile")
                private boolean createIndexFile;
            }

            public int stdOutLimit() {
                return stdOutLimit != null ? stdOutLimit : 1000;
            }
        }

        /**
         * CCAPI-specific settings
         */
        @Data
        @Accessors(fluent = true, chain = true)
        @XmlAccessorType(XmlAccessType.FIELD)
        public static class CcapiSettings {
            @XmlAttribute(name = "individualMessagesVisible")
            private boolean individualMessagesVisible;
        }
    }

    /**
     * Credential entry for API access
     */
    @Data
    @Accessors(fluent = true, chain = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Credential {

        @XmlAttribute(name = "id")
        private String id;

        @XmlAttribute(name = "name")
        private String name;

        @XmlAttribute(name = "email")
        private String email;

        @XmlAttribute(name = "orgId")
        private String orgId;

        @XmlAttribute(name = "key")
        private String key;

        @XmlAttribute(name = "active")
        private boolean active;

        @XmlAttribute(name = "tier", required = false)
        private Integer tier; // 0=free, 1=$20, 5=$100, 20=$200, etc.

        @XmlAttribute(name = "ua", required = false)
        private String ua; // Custom User-Agent string for this credential

        // Return tier with default value of 1 if not set
        public int tier() {
            return tier != null ? tier : 1;
        }
    }

    /**
     * Get all active credentials
     */
    public List<Credential> getActiveCredentials() {
        return credentials.stream()
                .filter(Credential::active)
                .toList();
    }

    /**
     * Get the first active credential (default)
     */
    public Credential getDefaultCredential() {
        return getActiveCredentials().stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Get the resolved ccapiBaseUrl with environment variable substitution.
     * Supports Windows-style environment variables like %CCAPI_BASE_URL%
     *
     * @return the ccapiBaseUrl with environment variables resolved, or the original value if no variables found
     */
    public String resolvedCcapiBaseUrl() {
        return resolveEnvironmentVariables(ccapiBaseUrl);
    }

    /**
     * Resolve environment variables in a string.
     * Supports Windows-style format: %VARIABLE_NAME%
     *
     * @param value the string that may contain environment variable references
     * @return the string with environment variables substituted, or the original value if null/empty
     */
    private static String resolveEnvironmentVariables(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Pattern to match %VARIABLE_NAME%
        var pattern = java.util.regex.Pattern.compile("%([A-Za-z0-9_]+)%");
        var matcher = pattern.matcher(value);
        var result = new StringBuilder();

        var lastEnd = 0;
        while (matcher.find()) {
            // Append text before the match
            result.append(value, lastEnd, matcher.start());

            // Get environment variable name and value
            var varName = matcher.group(1);
            var varValue = System.getenv(varName);

            // Append the resolved value or keep the original if not found
            if (varValue != null) {
                result.append(varValue);
            } else {
                // Keep original if environment variable not found
                result.append(matcher.group(0));
            }

            lastEnd = matcher.end();
        }

        // Append remaining text after last match
        result.append(value.substring(lastEnd));

        return result.toString();
    }
}
