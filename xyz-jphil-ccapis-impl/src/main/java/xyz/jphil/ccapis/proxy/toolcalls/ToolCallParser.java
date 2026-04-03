package xyz.jphil.ccapis.proxy.toolcalls;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import xyz.jphil.ccapis.proxy.anthropic.ToolUse;

import java.util.*;

import static xyz.jphil.ccapis.proxy.CcapiXmlElements.Tag.*;
import static xyz.jphil.ccapis.proxy.CcapiXmlElements.Attr.*;
import static xyz.jphil.ccapis.proxy.toolcalls.ConversionRuleType.*;

/**
 * Parses CCAPI response text containing XML tool calls and converts them to Anthropic format
 *
 * <p>Handles two formats (using Anthropic's tool_use tags):
 * <ul>
 *   <li>Format 1: &lt;tool_uses&gt;&lt;tool_use&gt;...&lt;/tool_use&gt;&lt;/tool_uses&gt;</li>
 *   <li>Format 2: &lt;tool_use&gt;...&lt;/tool_use&gt; (direct, no wrapper)</li>
 * </ul>
 *
 * <h2>CRITICAL: JSoup text() vs wholeText() vs html() for content extraction</h2>
 * <p>When extracting parameter content from XML elements using JSoup:
 * <ul>
 *   <li><b>text()</b> - Collapses whitespace to single spaces, strips ALL tags.
 *       Example: "line1\nline2\nline3" → "line1 line2 line3"
 *       Example: "&lt;project&gt;content&lt;/project&gt;" → "content"</li>
 *   <li><b>wholeText()</b> - Preserves newlines BUT STRIPS embedded XML/HTML tags.
 *       Example: "line1\nline2" → "line1\nline2" ✓
 *       Example: "&lt;project&gt;&lt;version&gt;1.0&lt;/version&gt;&lt;/project&gt;" → "1.0" ✗ (tags removed!)</li>
 *   <li><b>html()</b> - Preserves EVERYTHING: newlines, XML tags, HTML entities.
 *       Example: "line1\nline2" → "line1\nline2" ✓
 *       Example: "&lt;project&gt;&lt;version&gt;1.0&lt;/version&gt;&lt;/project&gt;" → full XML preserved ✓</li>
 * </ul>
 * <p><b>Why this matters:</b> Tool parameters often contain:
 * <ul>
 *   <li>Source code (preserves formatting)</li>
 *   <li>XML files like pom.xml (must preserve tags!)</li>
 *   <li>JSON, YAML, config files (preserve structure)</li>
 * </ul>
 * <p><b>Solution:</b> Use html() for parameter values to preserve embedded XML/HTML content.
 * Also ensure prettyPrint(false) is set on JSoup output settings to preserve original formatting.
 */
public class ToolCallParser {

    /**
     * Result of parsing containing text and tool calls
     */
    public static class ParseResult {
        private final String textBeforeToolCalls;
        private final List<ToolUse> toolUses;
        private final boolean hasToolCalls;

        public ParseResult(String textBeforeToolCalls, List<ToolUse> toolUses) {
            this.textBeforeToolCalls = textBeforeToolCalls;
            this.toolUses = toolUses;
            this.hasToolCalls = !toolUses.isEmpty();
        }

        public String getTextBeforeToolCalls() {
            return textBeforeToolCalls;
        }

        public List<ToolUse> getToolUses() {
            return toolUses;
        }

        public boolean hasToolCalls() {
            return hasToolCalls;
        }
    }

    /**
     * Parse text containing potential tool calls in XML format
     */
    public static ParseResult parse(String responseText) {
        return parse(responseText, null);
    }

    /**
     * Parse text containing potential tool calls in XML format with logging
     */
    public static ParseResult parse(String responseText, xyz.jphil.ccapis.proxy.DebugLogger logger) {
        if (responseText == null || responseText.isEmpty()) {
            return new ParseResult("", List.of());
        }

        // Parse the entire response with JSoup with whitespace and case preservation
        // CRITICAL: Use HTML parser (lenient) but with preserveCase setting
        // This maintains original tag casing (e.g., modelVersion stays modelVersion)
        // while still being lenient enough to handle malformed markup
        // ALSO CRITICAL: Enable position tracking to extract raw source later
        var parser = org.jsoup.parser.Parser.htmlParser();
        parser.settings(org.jsoup.parser.ParseSettings.preserveCase);
        parser.setTrackPosition(true); // Enable source position tracking (JSoup 1.17+)
        var jsoupDoc = parser.parseInput(responseText, "");
        jsoupDoc.outputSettings().prettyPrint(false); // Essential for preserving \n

        var body = jsoupDoc.body();

        // Check for tool_uses wrapper OR direct tool_use elements
        var toolUsesElements = body.select(tool_uses.v());
        var toolUseElements = body.select(tool_use.v());

        // Determine which format we're dealing with
        Elements toolElements;
        Element firstToolElement;

        if (!toolUsesElements.isEmpty()) {
            // Format 1: <tool_uses> wrapper exists
            firstToolElement = toolUsesElements.first();
            toolElements = firstToolElement.select(tool_use.v());
        } else if (!toolUseElements.isEmpty()) {
            // Format 2: Direct <tool_use> elements without wrapper
            firstToolElement = toolUseElements.first();
            toolElements = toolUseElements;
        } else {
            // No tool calls found
            return new ParseResult(responseText, List.of());
        }

        // Extract text before first tool element
        // IMPORTANT: Use wholeText()/getWholeText() to preserve line breaks and formatting
        var textBeforeBuilder = new StringBuilder();
        for (var node : body.childNodes()) {
            if (node.equals(firstToolElement)) {
                break;
            }
            if (node instanceof org.jsoup.nodes.TextNode textNode) {
                // TextNode.getWholeText() preserves original whitespace including \n
                textBeforeBuilder.append(textNode.getWholeText());
            } else if (node instanceof org.jsoup.nodes.Element elem &&
                       !elem.tagName().equals(tool_uses.v()) &&
                       !elem.tagName().equals(tool_use.v())) {
                // Element.wholeText() preserves formatting
                textBeforeBuilder.append(elem.wholeText());
            }
        }

        String textBefore = textBeforeBuilder.toString().trim();

        // Parse each use_tool element
        List<ToolUse> toolUses = new ArrayList<>();

        for (var toolElem : toolElements) {
            String toolName = toolElem.attr(name.v());
            Map<String, Object> input = new LinkedHashMap<>();

            // Extract parameters
            for (var paramElem : toolElem.select(parameter.v())) {
                String paramName = paramElem.attr(name.v());
                // Extract parameter value from original source using position tracking
                // This preserves exact content including <?xml?> declarations
                // See extractParameterValue() for details
                String paramValueStr = extractParameterValue(paramElem, responseText);

                // Type conversion: XML stores everything as strings, but Anthropic API expects typed values
                // Convert numeric/boolean strings to proper types for API compatibility
                Object paramValue = convertParameterType(toolName, paramName, paramValueStr, logger);
                input.put(paramName, paramValue);
            }

            // Generate unique tool use ID
            String toolUseId = "toolu_" + System.currentTimeMillis() + "_" + toolUses.size();

            toolUses.add(ToolUse.builder()
                .id(toolUseId)
                .name(toolName)
                .input(input)
                .build());
        }

        return new ParseResult(textBefore, toolUses);
    }

    /**
     * Extract parameter value from original source using JSoup's position tracking.
     *
     * <p><b>SOURCE OF TRUTH APPROACH (PRP-22 Final Fix):</b> Instead of using the parsed
     * tree (which HTML parser corrupts), we extract the RAW content from the original
     * source string using JSoup's {@code sourceRange()} API. This preserves:
     * <ul>
     *   <li>XML declarations: {@code <?xml?>} stays as-is (not converted to comment)</li>
     *   <li>Tag case: Already handled by parser with {@code ParseSettings.preserveCase}</li>
     *   <li>HTML entities: We decode them from the raw source</li>
     *   <li>All whitespace and formatting: Exactly as sent by CCAPI</li>
     * </ul>
     *
     * <p><b>How it works:</b>
     * <ol>
     *   <li>Parser enabled with {@code setTrackPosition(true)}</li>
     *   <li>Each node tracks its position in original source</li>
     *   <li>We extract {@code <parameter>...</parameter>} from source</li>
     *   <li>We extract inner content (between tags)</li>
     *   <li>We decode HTML entities from this raw content</li>
     * </ol>
     *
     * <p>Example:
     * <pre>
     * Original source: &lt;parameter name="content"&gt;&lt;?xml version="1.0"?&gt;&lt;project&gt;...&lt;/project&gt;&lt;/parameter&gt;
     * sourceRange gives us positions of the parameter element
     * We extract: &lt;?xml version="1.0"?&gt;&lt;project&gt;...&lt;/project&gt; (raw, before HTML parser corrupts it)
     * We decode entities and return the exact content ✓
     * </pre>
     *
     * @param paramElem the parameter element to extract value from
     * @param originalSource the original unparsed source text
     * @return the parameter value with exact content as received from server
     */
    private static String extractParameterValue(Element paramElem, String originalSource) {
        // Try to use source position tracking (JSoup 1.17+)
        var sourceRange = paramElem.sourceRange();

        if (sourceRange != null && originalSource != null) {
            try {
                // Get the element's position in original source
                // IMPORTANT: sourceRange.end() gives position AFTER the opening tag (e.g., after '>'),
                // NOT the position of the closing tag! We need to find the closing tag manually.
                int startPos = sourceRange.start().pos();
                int openingTagEnd = sourceRange.end().pos();

                // Find the closing tag manually in the original source
                String tagName = paramElem.tagName();
                String closingTag = "</" + tagName + ">";
                int closingTagStart = originalSource.indexOf(closingTag, openingTagEnd);
                if (closingTagStart == -1) {
                    // Closing tag not found, fall back
                    System.err.println("[ToolCallParser] Closing tag " + closingTag + " not found after position " + openingTagEnd);
                    return fallbackExtract(paramElem);
                }
                int actualEndPos = closingTagStart + closingTag.length();

                // Extract the raw outer HTML: <parameter name="X">CONTENT</parameter>
                String rawOuterHtml = originalSource.substring(startPos, actualEndPos);

                // Find the inner content (between opening and closing tags)
                // Opening tag ends at first '>' after start
                int contentStart = rawOuterHtml.indexOf('>');
                if (contentStart == -1) {
                    // Malformed, fall back
                    return fallbackExtract(paramElem);
                }
                contentStart++; // Move past the '>'

                // Closing tag starts at last '<' before end
                int contentEnd = rawOuterHtml.lastIndexOf('<');
                if (contentEnd == -1 || contentEnd <= contentStart) {
                    // Malformed or empty, fall back
                    return fallbackExtract(paramElem);
                }

                // Extract raw inner content (this has <?xml?> intact AND <source> closing tags!)
                String rawContent = rawOuterHtml.substring(contentStart, contentEnd);

                // Decode HTML entities from the raw content
                // This converts &amp; -> &, &lt; -> <, etc.
                String decodedContent = org.jsoup.parser.Parser.unescapeEntities(rawContent, false);

                return decodedContent;

            } catch (Exception e) {
                // Position tracking failed, fall back
                System.err.println("[ToolCallParser] sourceRange extraction failed: " + e.getMessage());
                return fallbackExtract(paramElem);
            }
        }

        // Position tracking not available or failed, use fallback
        return fallbackExtract(paramElem);
    }

    /**
     * Fallback extraction method when sourceRange is not available.
     * Uses the parsed tree with post-processing (less reliable but functional).
     */
    private static String fallbackExtract(Element paramElem) {
        String content = paramElem.html();

        // Fix XML declarations converted to comments (only at start)
        content = content.replaceFirst("^<!--\\?xml\\s+([^?]+)\\?-->", "<?xml $1?>");

        // Decode entities
        content = org.jsoup.parser.Parser.unescapeEntities(content, false);

        return content;
    }

    /**
     * Recursively reconstruct content from JSoup node tree.
     * Handles TextNode, Element, DataNode, and Comment nodes.
     *
     * @deprecated As of PRP-22 (2025-11-10), replaced by simpler approach using
     * {@code paramElem.html()} with post-processing. This method had issues with:
     * <ul>
     *   <li>Tag case: Used {@code tagName()} which returns lowercase normalized names</li>
     *   <li>XML declarations: Could not handle {@code <?xml?>} processing instructions</li>
     * </ul>
     * New approach: Use HTML parser with {@code ParseSettings.preserveCase}, then
     * post-process to fix XML declarations and decode entities.
     *
     * <p>Kept for reference and potential future use if needed.
     *
     * <p>Note: We don't expect ProcessingInstruction, DocumentType, or other
     * document-level nodes at this level (inside tool call parameters).
     * If encountered, they will be silently ignored.
     *
     * <p>Strategy:
     * <ul>
     *   <li>TextNode: Use getWholeText() - returns RAW text as parsed by JSoup</li>
     *   <li>Element: Manually serialize with tags and recursively process children</li>
     *   <li>DataNode: Use getWholeData() - for &lt;script&gt;, &lt;style&gt; content</li>
     *   <li>Comment: Preserve as HTML comment</li>
     * </ul>
     *
     * <p>Why this works correctly:
     * <ul>
     *   <li>If CCAPI sent "&&", JSoup parsed it as "&&", we get "&&" ✓</li>
     *   <li>If CCAPI sent "&amp;&amp;", JSoup parsed it as "&&", we get "&&" ✓</li>
     *   <li>If CCAPI sent "&lt;xml&gt;", JSoup parsed as Element, we reconstruct "&lt;xml&gt;" ✓</li>
     * </ul>
     *
     * @param element the element whose children to reconstruct
     * @param result the StringBuilder to append reconstructed content to
     */
    private static void reconstructContent(Element element, StringBuilder result) {
        for (var child : element.childNodes()) {
            if (child instanceof org.jsoup.nodes.TextNode textNode) {
                // TextNode: Get raw text as parsed (no HTML encoding)
                result.append(textNode.getWholeText());

            } else if (child instanceof Element childElement) {
                // Element: Reconstruct with tags
                result.append("<").append(childElement.tagName());

                // Add attributes if any
                for (var attr : childElement.attributes()) {
                    result.append(" ")
                          .append(attr.getKey())
                          .append("=\"")
                          .append(attr.getValue()) // Attribute values are already unescaped by JSoup
                          .append("\"");
                }
                result.append(">");

                // Recursively process child content
                reconstructContent(childElement, result);

                // Closing tag
                result.append("</").append(childElement.tagName()).append(">");

            } else if (child instanceof org.jsoup.nodes.DataNode dataNode) {
                // DataNode: Used in <script>, <style> tags - get raw data
                result.append(dataNode.getWholeData());

            } else if (child instanceof org.jsoup.nodes.Comment commentNode) {
                // Comment: Preserve comments if present
                result.append("<!--").append(commentNode.getData()).append("-->");

            } else if (child instanceof org.jsoup.nodes.XmlDeclaration xmlDecl) {
                // XML Declaration: <?xml version="1.0" encoding="UTF-8"?>
                // XmlDeclaration extends Node and represents processing instructions
                result.append(xmlDecl.outerHtml());

            } else if (child instanceof org.jsoup.nodes.DocumentType docType) {
                // DocumentType: <!DOCTYPE html>
                result.append(docType.outerHtml());
            }
            // Note: Other node types (if any) will be silently ignored
        }
    }

    public static ConversionRule rule(String parentTagPattern, String paramPattern, ConversionRuleType type){
        return new ConversionRule(parentTagPattern, paramPattern, type);
    }

    /**
     * Conversion rules list: tool pattern + param pattern -> rule type
     * Use ".*" to match any tool, specific patterns for tool-specific params
     */
    private static final List<ConversionRule> CONVERSION_RULES = List.of(
        // Universal numeric parameters (work across all tools)
        rule(".*", "offset", KNOWN_NUMERIC),
        rule(".*", "limit", KNOWN_NUMERIC),
        rule(".*", "timeout", KNOWN_NUMERIC),
        rule(".*", "port", KNOWN_NUMERIC),
        rule(".*", "line", KNOWN_NUMERIC),
        rule(".*", "count", KNOWN_NUMERIC),
        rule(".*", "size", KNOWN_NUMERIC),
        rule(".*", "length", KNOWN_NUMERIC),
        rule(".*", "index", KNOWN_NUMERIC),
        rule(".*", "number", KNOWN_NUMERIC),
        rule(".*", "num", KNOWN_NUMERIC),

        // Known string parameters (never convert even if numeric)
        rule(".*", "id", KNOWN_STRING),
        rule(".*", "file_path", KNOWN_STRING),
        rule(".*", "path", KNOWN_STRING),
        rule(".*", "name", KNOWN_STRING),
        rule(".*", "description", KNOWN_STRING)

        // As we discover more patterns through empirical evidence, add them here
    );

    /**
     * Convert XML parameter string values to appropriate types for Anthropic API
     *
     * <p>Uses context-aware rules based on tool_name + param_name patterns.
     * Logs all conversions to dedicated file for analysis.
     *
     * <p><b>PRP-23 Enhancement:</b> Now handles JSON arrays and objects by parsing them
     * into proper Java structures (List, Map) instead of leaving them as strings.
     *
     * <p><b>IMPORTANT - Separation of Concerns:</b>
     * <ul>
     *   <li><b>Stage 1 (extractParameterValue):</b> Extracts RAW content from XML source
     *       <ul>
     *         <li>Preserves XML declarations: {@code <?xml?>}</li>
     *         <li>Preserves tag case: {@code <modelVersion>}</li>
     *         <li>Preserves multi-line text</li>
     *         <li>Decodes HTML entities</li>
     *         <li>Returns: ALWAYS a STRING containing exact content</li>
     *       </ul>
     *   </li>
     *   <li><b>Stage 2 (convertParameterType - THIS METHOD):</b> Type conversion only
     *       <ul>
     *         <li>Takes the extracted STRING</li>
     *         <li>Detects and converts to proper types</li>
     *         <li>Does NOT modify content, only converts type</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * <p><b>Type Conversion Logic (Non-Overlapping Conditions):</b>
     * <ol>
     *   <li>Booleans: "true"/"false" (case-insensitive) → {@code Boolean}</li>
     *   <li>JSON: Starts with "[" or "{" → {@code List<Map>} or {@code Map}
     *       <ul><li>XML starts with "&lt;" so SKIPS this check ✓</li></ul>
     *   </li>
     *   <li>Numbers: Matches "-?\d+(\.\d+)?" → {@code Integer/Long/Double}
     *       <ul><li>XML/multiline text not numeric so SKIPS this check ✓</li></ul>
     *   </li>
     *   <li>Everything else: Returns as-is → {@code String}
     *       <ul><li>XML content, multiline text, file paths all end up here ✓</li></ul>
     *   </li>
     * </ol>
     *
     * <p><b>Why XML/Multiline Content is NOT Affected:</b>
     * XML content like {@code <?xml?><project><modelVersion>...} will:
     * <ul>
     *   <li>❌ Not boolean (doesn't equal "true"/"false")</li>
     *   <li>❌ Not JSON (starts with "&lt;" not "[" or "{")</li>
     *   <li>❌ Not numeric (fails regex check)</li>
     *   <li>✅ Returns as STRING unchanged (correct behavior!)</li>
     * </ul>
     *
     * @param toolName name of the tool being invoked
     * @param paramName parameter name
     * @param value string value from XML (already extracted with proper formatting)
     * @param logger debug logger for conversion logging
     * @return properly typed value (Integer, Long, Double, Boolean, String, List, Map)
     */
    private static Object convertParameterType(String toolName, String paramName, String value,
                                               xyz.jphil.ccapis.proxy.DebugLogger logger) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return value; // Preserve empty strings
        }

        // Step 1: Boolean values (always convert, case-insensitive)
        if (trimmed.equalsIgnoreCase("true")) {
            logConversion(logger, toolName, paramName, value, "true (boolean)", "BOOLEAN", false);
            return true;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            logConversion(logger, toolName, paramName, value, "false (boolean)", "BOOLEAN", false);
            return false;
        }

        // Step 2: JSON arrays and objects (PRP-23)
        // Detect if value starts with [ or { (JSON array or object)
        // NOTE: XML starts with < so will SKIP this check and fall through to Step 4 (return as string)
        // This ensures XML content is NOT treated as JSON and remains unchanged
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Object parsed = objectMapper.readValue(trimmed, Object.class);
                // Returns: List<LinkedHashMap> for arrays, LinkedHashMap for objects
                logConversion(logger, toolName, paramName,
                    value.length() > 50 ? value.substring(0, 50) + "..." : value,
                    parsed.getClass().getSimpleName(), "JSON", false);
                return parsed;
            } catch (Exception e) {
                // Not valid JSON, treat as string and log warning
                logConversion(logger, toolName, paramName, value, value + " (invalid JSON, kept as string)",
                    "JSON_PARSE_ERROR", true);
                System.err.println("[ToolCallParser] JSON parse failed for " + toolName + "." + paramName + ": " + e.getMessage());
                return value;
            }
        }

        // Step 3: Check if value is castable as number
        boolean isNumeric = trimmed.matches("-?\\d+(\\.\\d+)?");
        if (!isNumeric) {
            // Not numeric, keep as string (no logging needed for obvious strings)
            // XML content, multiline text, file paths, commands all end up here ✓
            return value;
        }

        // Step 4: Value is numeric - check conversion rules
        ConversionRuleType rule = findConversionRule(toolName, paramName);

        switch (rule) {
            case KNOWN_NUMERIC -> {
                // Known numeric parameter - convert and log to file only
                Object converted = parseNumber(trimmed);
                logConversion(logger, toolName, paramName, value, converted.toString(),
                    "KNOWN_NUMERIC", false);
                return converted;
            }
            case KNOWN_STRING -> {
                // Known string parameter - do NOT convert, log warning
                logConversion(logger, toolName, paramName, value, value + " (kept as string)",
                    "KNOWN_STRING", true);
                return value;
            }
            case UNKNOWN -> {
                // Unknown parameter with numeric value - convert but warn
                Object converted = parseNumber(trimmed);
                logConversion(logger, toolName, paramName, value, converted.toString(),
                    "UNKNOWN", true);
                return converted;
            }
        }

        return value; // Should never reach here
    }

    /**
     * Find conversion rule for tool + param combination
     * Iterates through rules list and matches using regex patterns
     */
    private static ConversionRuleType findConversionRule(String toolName, String paramName) {
        // Iterate through all rules and find first match
        for (var rule : CONVERSION_RULES) {
            // Check if tool name matches pattern and param name matches exactly
            if (toolName.matches(rule.parentTagPattern()) && paramName.equals(rule.paramPattern())) {
                return rule.type();
            }
        }

        // No match found - return UNKNOWN
        return UNKNOWN;
    }

    /**
     * Parse numeric string to appropriate type (Integer, Long, or Double)
     */
    private static Object parseNumber(String value) {
        try {
            if (!value.contains(".")) {
                long longValue = Long.parseLong(value);
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    return (int) longValue;
                }
                return longValue;
            }
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return value; // Fallback to string
        }
    }

    /**
     * Log type conversion decision
     * @param warnToConsole if true, also log to console (for unknown conversions)
     */
    private static void logConversion(xyz.jphil.ccapis.proxy.DebugLogger logger,
                                      String toolName, String paramName,
                                      String originalValue, String convertedValue,
                                      String ruleType, boolean warnToConsole) {
        String logMsg = String.format("[TYPE_CONVERSION] tool=%s, param=%s, original=\"%s\", converted=%s, rule=%s",
            toolName, paramName, originalValue, convertedValue, ruleType);

        // Always log to file
        if (logger != null) {
            logger.logTypeConversion(logMsg);
        }

        // Log to console if unknown conversion
        if (warnToConsole) {
            System.out.println("[WARNING] " + logMsg);
        }
    }
}
