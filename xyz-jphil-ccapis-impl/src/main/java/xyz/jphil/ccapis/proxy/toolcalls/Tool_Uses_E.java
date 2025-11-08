package xyz.jphil.ccapis.proxy.toolcalls;

import luvml.element.SemanticBlockContainerElement;
import luvml.element.SemanticElementTagNameClassNameMapping.LowerCase_E;
import java.util.List;

/**
 * Semantic element for <tool_uses> XML tag from CCAPI response
 * Note: Using tool_uses to align with Anthropic's tool_use terminology
 */
public class Tool_Uses_E extends SemanticBlockContainerElement<Tool_Uses_E> implements LowerCase_E {
    public Tool_Uses_E() {
        super(Tool_Uses_E.class);
    }

    /**
     * Get all <use_tool> children
     */
    public List<Use_Tool_E> invocations() {
        return findChildren(Use_Tool_E.class);
    }
}
