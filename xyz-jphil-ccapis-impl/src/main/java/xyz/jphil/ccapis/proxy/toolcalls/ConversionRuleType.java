package xyz.jphil.ccapis.proxy.toolcalls;

/**
     * Type conversion rules for tool parameters (context-aware)
     * Format: "tool_pattern:param_pattern" -> ConversionRule
     *
     * Categories:
     * - KNOWN_NUMERIC: Definitely convert to number
     * - KNOWN_STRING: Never convert (even if looks numeric)
     * - UNKNOWN: Not in rules, decide based on value content + log warning
     */
public enum ConversionRuleType {
    KNOWN_NUMERIC,   // Convert to number
    KNOWN_STRING,    // Keep as string
    UNKNOWN          // Unknown - will log warning
}
