package xyz.jphil.ccapis.proxy.toolcalls;

import luvml.element.SemanticBlockContainerElement;
import luvml.element.SemanticElementTagNameClassNameMapping.LowerCase_E;
import luvx.Text_I;
import luvx.ContainerElement_I;

/**
 * Semantic element for <parameter name="paramName">value</parameter> XML tag from CCAPI response
 */
public class Parameter_E extends SemanticBlockContainerElement<Parameter_E> implements LowerCase_E {
    public static final String $name = "name";

    public Parameter_E() {
        super(Parameter_E.class);
    }

    /**
     * Get parameter name from 'name' attribute
     */
    public String paramName() {
        return attr($name);
    }

    /**
     * Get parameter value from element with preserved whitespace (newlines, spaces, etc.)
     *
     * Uses luvml's wholeText() API which preserves all formatting:
     * - Newlines (\n)
     * - Tabs (\t)
     * - Multiple spaces
     * - All other whitespace
     */
    public String paramValue() {
        var result = new StringBuilder();
        extractWholeText(this, result);
        return result.toString();
    }

    /**
     * Recursively extract whole text content while preserving all whitespace.
     * Uses Text_I.wholeText() which preserves newlines and spaces.
     */
    private void extractWholeText(ContainerElement_I<?> container, StringBuilder result) {
        for (var node : container.childNodes()) {
            if (node instanceof Text_I<?> textNode) {
                // Use wholeText() to preserve all whitespace (NOT text() which normalizes!)
                result.append(textNode.wholeText());
            } else if (node instanceof ContainerElement_I<?> childContainer) {
                // Recursively process child containers
                extractWholeText(childContainer, result);
            }
        }
    }
}
