package ca.yorku.storyteller.llm;

import java.util.Map;

/**
 * A tool the model asked to run, as the model sent it. The agent turns it into an
 * {@code agent.tools.ToolCall} and the app validates it before anything changes.
 *
 * @param id        the provider's id for this call, echoed back in the matching {@link LLMToolResult}
 * @param name      the tool name
 * @param arguments argument values as text, keyed by parameter name
 */
public record LLMToolCall(String id, String name, Map<String, String> arguments) {

    public LLMToolCall {
        arguments = Map.copyOf(arguments);
    }
}
