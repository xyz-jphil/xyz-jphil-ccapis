package xyz.jphil.ccapis.proxy.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 * Anthropic tool_use content block format
 *
 * Represents a tool invocation in the Anthropic API format
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolUse {

    /**
     * Type - always "tool_use"
     */
    @Builder.Default
    String type = "tool_use";

    /**
     * Unique identifier for this tool use
     */
    String id;

    /**
     * Name of the tool being invoked
     */
    String name;

    /**
     * Input parameters for the tool as a JSON object
     */
    Map<String, Object> input;
}
