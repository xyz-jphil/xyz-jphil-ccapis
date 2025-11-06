package xyz.jphil.ccapis.util;

import java.util.regex.Pattern;

/**
 * Utility for resolving environment variables in strings.
 * Supports Windows-style format: %VARIABLE_NAME%
 */
public class EnvironmentVariableResolver {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("%([A-Za-z0-9_]+)%");

    /**
     * Resolve environment variables in a string.
     * Supports Windows-style format: %VARIABLE_NAME%
     *
     * @param value the string that may contain environment variable references
     * @return the string with environment variables substituted, or the original value if null/empty
     */
    public static String resolve(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        var matcher = ENV_VAR_PATTERN.matcher(value);
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
