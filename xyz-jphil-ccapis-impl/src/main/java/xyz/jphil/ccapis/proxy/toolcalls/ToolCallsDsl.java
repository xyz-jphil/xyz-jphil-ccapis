package xyz.jphil.ccapis.proxy.toolcalls;

import luvml.HtmlAttribute;
import luvx.Frag_I;

/**
 * DSL for creating tool call semantic elements programmatically
 */
public final class ToolCallsDsl {

    private ToolCallsDsl() {} // Prevent instantiation

    // Element factories
    public static Tool_Uses_E toolUses(Frag_I... fragments) {
        return new Tool_Uses_E().____(fragments);
    }

    public static Use_Tool_E useTool(Frag_I... fragments) {
        return new Use_Tool_E().____(fragments);
    }

    public static Parameter_E parameter(Frag_I... fragments) {
        return new Parameter_E().____(fragments);
    }

    // Attribute factories
    public static HtmlAttribute name(String value) {
        return new HtmlAttribute(Use_Tool_E.$name, value);
    }

    public static HtmlAttribute paramName(String value) {
        return new HtmlAttribute(Parameter_E.$name, value);
    }
}
