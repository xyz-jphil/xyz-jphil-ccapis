package xyz.jphil.ccapis.proxy.toolcalls;

import luvml.element.SemanticBlockContainerElement;
import luvml.element.SemanticElementTagNameClassNameMapping.LowerCase_E;
import java.util.List;

/**
 * Semantic element for <use_tool name="ToolName"> XML tag from CCAPI response
 * Class name Use_Tool_E converts to tag name "use_tool" via LowerCase_E
 */
public class Use_Tool_E extends SemanticBlockContainerElement<Use_Tool_E> implements LowerCase_E {
    public static final String $name = "name";

    public Use_Tool_E() {
        super(Use_Tool_E.class);
    }

    /**
     * Get tool name from 'name' attribute
     */
    public String toolName() {
        return attr($name);
    }

    /**
     * Get all <parameter> children
     */
    public List<Parameter_E> parameters() {
        return findChildren(Parameter_E.class);
    }
}
