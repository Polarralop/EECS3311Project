package ca.yorku.storyteller.agent.tools;

import java.util.Map;

/**
 * A tool request returned by the model.
 *
 * <p>Features: F04.
 *
 * @param id        the model's id for the call, echoed back with the result
 * @param toolName  the tool the model asked for
 * @param arguments argument values as text, keyed by parameter name
 */
public record ToolCall(String id, String toolName, Map<String, String> arguments) {

    public ToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    /** The argument, stripped, or an empty string when it is missing. */
    public String argument(String name) {
        String value = arguments.get(name);
        return value == null ? "" : value.strip();
    }
}
