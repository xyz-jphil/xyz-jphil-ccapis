package xyz.jphil.ccapis.proxy.toolcalls;

import luvml.element.SemanticBlockContainerElement;
import luvml.element.SemanticElementTagNameClassNameMapping.LowerCase_E;

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
     * Get parameter value from element text content
     */
    public String paramValue() {
        return textContent().trim();
    }
}
