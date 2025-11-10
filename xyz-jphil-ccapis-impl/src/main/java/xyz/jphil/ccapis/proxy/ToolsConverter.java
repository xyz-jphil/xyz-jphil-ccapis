package xyz.jphil.ccapis.proxy;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static xyz.jphil.ccapis.proxy.CcapiXmlElements.*;

/**
 * Converts Anthropic API tool definitions to text format for CCAPI
 *
 * <p>Anthropic API uses structured JSON for tools, but CCAPI only accepts plain text.
 * This converter transforms tool definitions into XML-style text that Claude can understand.
 */
public class ToolsConverter {

    /**
     * Convert Anthropic tools format to XML text description
     *
     * @param tools List of tool definitions in Anthropic JSON format
     * @return Text description of tools in XML format, or empty string if no tools
     */
    public static String convertToolsToText(List<JsonNode> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }

        var result = new StringBuilder();
        result.append("# Available Tools\n\n");
        result.append("IMPORTANT: You MUST use <tool_use> tags (Anthropic format). Do NOT use <invoke> or <use_tool> tags.\n\n");
        result.append("Use tools by outputting XML in EXACTLY this format:\n");
        result.append(TOOL_CALL_EXAMPLE).append("\n\n");
        result.append("CRITICAL: Use <tool_use name=\"...\"> with the standard Anthropic format. The tag name MUST be 'tool_use'.\n\n");

        // Convert each tool
        for (var tool : tools) {
            convertSingleTool(tool, result);
        }

        return result.toString();
    }

    /**
     * Convert a single tool definition to text
     */
    private static void convertSingleTool(JsonNode tool, StringBuilder result) {
        // Get tool name
        var name = tool.has("name") ? tool.get("name").asText() : "unknown";
        result.append("## Tool: ").append(name).append("\n\n");

        // Get description
        if (tool.has("description")) {
            result.append("**Description:** ").append(tool.get("description").asText()).append("\n\n");
        }

        // Get input schema (parameters)
        if (tool.has("input_schema")) {
            convertInputSchema(tool.get("input_schema"), result);
        }

        result.append("\n");
    }

    /**
     * Convert input schema (parameters) to text
     */
    private static void convertInputSchema(JsonNode schema, StringBuilder result) {
        result.append("**Parameters:**\n\n");

        if (!schema.has("properties")) {
            result.append("  (No parameters)\n");
            return;
        }

        var properties = schema.get("properties");
        var required = schema.has("required") ? schema.get("required") : null;

        // Convert each parameter
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            var paramName = field.getKey();
            var paramDef = field.getValue();

            result.append("  - `").append(paramName).append("`");

            // Check if required
            boolean isRequired = false;
            if (required != null && required.isArray()) {
                for (var req : required) {
                    if (req.asText().equals(paramName)) {
                        isRequired = true;
                        break;
                    }
                }
            }

            if (isRequired) {
                result.append(" **(required)**");
            }

            // Add type
            if (paramDef.has("type")) {
                result.append(" - Type: `").append(paramDef.get("type").asText()).append("`");
            }

            result.append("\n");

            // Add description
            if (paramDef.has("description")) {
                result.append("    ").append(paramDef.get("description").asText()).append("\n");
            }

            // Add enum values if present
            if (paramDef.has("enum")) {
                result.append("    Allowed values: ");
                var enumValues = paramDef.get("enum");
                for (int i = 0; i < enumValues.size(); i++) {
                    if (i > 0) result.append(", ");
                    result.append("`").append(enumValues.get(i).asText()).append("`");
                }
                result.append("\n");
            }

            result.append("\n");
        }
    }
}
