package xyz.jphil.ccapis.proxy.toolcalls;

import luvml.jsoup2luvml.SemanticElementConverter;
import org.jsoup.Jsoup;
import xyz.jphil.ccapis.proxy.anthropic.ToolUse;

import java.util.*;

import static luvml.jsoup2luvml.SemanticElementConverter.*;

/**
 * Parses CCAPI response text containing XML tool calls and converts them to Anthropic format
 */
public class ToolCallParser {

    private static final SemanticElementConverter CONVERTER = semanticElementConverter(
        def(Tool_Uses_E::new),
        def(Use_Tool_E::new),
        def(Parameter_E::new)
    );

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
        if (responseText == null || responseText.isEmpty()) {
            return new ParseResult("", List.of());
        }

        // Parse the entire response with JSoup with whitespace preservation
        // CRITICAL: prettyPrint(false) is needed to keep newlines!
        var jsoupDoc = Jsoup.parseBodyFragment(responseText);
        jsoupDoc.outputSettings().prettyPrint(false); // Essential for preserving \n

        var body = jsoupDoc.body();

        // Check if tool_uses element exists
        var toolUsesElements = body.select("tool_uses");
        if (toolUsesElements.isEmpty()) {
            // No tool calls - return full text
            return new ParseResult(responseText, List.of());
        }

        // Extract text before tool_uses (all text nodes before the element)
        var toolUsesElement = toolUsesElements.first();
        var textBeforeBuilder = new StringBuilder();

        // Get all text nodes before tool_uses
        for (var node : body.childNodes()) {
            if (node.equals(toolUsesElement)) {
                break;
            }
            if (node instanceof org.jsoup.nodes.TextNode textNode) {
                textBeforeBuilder.append(textNode.text());
            } else if (node instanceof org.jsoup.nodes.Element elem && !elem.tagName().equals("tool_uses")) {
                textBeforeBuilder.append(elem.text());
            }
        }

        String textBefore = textBeforeBuilder.toString().trim();

        // Convert the tool_uses element using LUVML
        var fragments = CONVERTER.convertMixedFragment(toolUsesElement.outerHtml());

        // Extract tool calls - simply iterate through fragments and find Tool_Uses_E elements
        List<ToolUse> toolUses = new ArrayList<>();

        for (var frag : fragments.fragments()) {
            if (frag instanceof Tool_Uses_E fc) {
                // Process each <use_tool> element
                for (var invoke : fc.invocations()) {
                    String toolName = invoke.toolName();
                    Map<String, Object> input = new LinkedHashMap<>();

                    // Extract parameters
                    for (var param : invoke.parameters()) {
                        input.put(param.paramName(), param.paramValue());
                    }

                    // Generate unique tool use ID
                    String toolUseId = "toolu_" + System.currentTimeMillis() + "_" + toolUses.size();

                    toolUses.add(ToolUse.builder()
                        .id(toolUseId)
                        .name(toolName)
                        .input(input)
                        .build());
                }
            }
        }

        return new ParseResult(textBefore, toolUses);
    }
}
