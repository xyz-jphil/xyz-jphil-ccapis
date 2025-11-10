package xyz.jphil.ccapis.proxy;

/**
 * XML element and attribute names used in CCAPI tool call format
 *
 * <p>Provides type-safe constants for XML parsing and generation:
 * <ul>
 *   <li>Enum names match XML tag/attribute names exactly (lowercase with underscores)</li>
 *   <li>Use .v() to get string value for JSoup/XML operations</li>
 *   <li>IDE refactoring support via enum values</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * import static xyz.jphil.ccapis.proxy.CcapiXmlElements.Tag.*;
 * import static xyz.jphil.ccapis.proxy.CcapiXmlElements.Attr.*;
 *
 * // JSoup selectors
 * body.select(use_tool.v())
 * body.select(use_tool.v() + " > " + parameter.v())
 * element.attr(name.v())
 *
 * // XML generation
 * String xml = "&lt;" + use_tool + " " + name + "=\"Write\"&gt;";
 * </pre>
 */
public final class CcapiXmlElements {

    private CcapiXmlElements() {
        // Prevent instantiation
    }

    /**
     * XML element tag names used in CCAPI tool calls (Anthropic format)
     */
    public enum Tag {
        /** Root wrapper element for tool calls: &lt;tool_uses&gt; */
        tool_uses,

        /** Individual tool invocation: &lt;tool_use name="ToolName"&gt; (Anthropic format) */
        tool_use,

        /** Tool parameter: &lt;parameter name="param_name"&gt;value&lt;/parameter&gt; */
        parameter;

        /**
         * Get tag name as string for XML/JSoup operations
         * @return tag name (e.g., "tool_use")
         */
        public String v() {
            return name();
        }
    }

    /**
     * XML attribute names used in CCAPI tool calls
     */
    public enum Attr {
        /** Name attribute for tools and parameters */
        name;

        /**
         * Get attribute name as string for XML/JSoup operations
         * @return attribute name (e.g., "name")
         */
        public String v() {
            return name();
        }
    }

    /**
     * Example XML showing correct tool call format (Anthropic format)
     * Used in system prompts to demonstrate syntax to Claude
     */
    public static final String TOOL_CALL_EXAMPLE =
        "<tool_uses>" +
            "<tool_use name=\"tool_name\">" +
                "<parameter name=\"param_name\">value</parameter>" +
            "</tool_use>" +
        "</tool_uses>";
}
