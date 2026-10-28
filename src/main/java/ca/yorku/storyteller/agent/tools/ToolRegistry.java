package ca.yorku.storyteller.agent.tools;

import ca.yorku.storyteller.llm.LLMToolSpec;
import ca.yorku.storyteller.model.StorySession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the available tools and executes the tool calls the model makes. This is the only way the
 * model reads or changes the story: every call is checked here before a tool runs, and a bad call
 * comes back to the model as a failed {@link ToolResult} instead of an exception.
 *
 * <p>Features: F04 F06 F07.
 * <p>Patterns: Facade (Subsystem).
 */
public class ToolRegistry {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final Map<String, List<String>> required = new LinkedHashMap<>();

    /**
     * Adds a tool.
     *
     * @throws IllegalArgumentException if a tool with the same name is registered or its schema is not valid JSON
     */
    public void register(AgentTool tool) {
        if (tools.containsKey(tool.getName())) {
            throw new IllegalArgumentException("A tool named " + tool.getName() + " is already registered.");
        }
        required.put(tool.getName(), requiredArguments(tool));
        tools.put(tool.getName(), tool);
    }

    /** The tool definitions to send with each request, in registration order. */
    public List<LLMToolSpec> schemas() {
        List<LLMToolSpec> specs = new ArrayList<>();
        for (AgentTool t : tools.values()) {
            specs.add(new LLMToolSpec(t.getName(), t.getDescription(), t.getSchema()));
        }
        return specs;
    }

    /**
     * Validates the call and runs the matching tool. Never throws for a bad call: an unknown tool, a
     * missing argument or a tool that refuses produces a failed result explaining why.
     */
    public ToolResult execute(ToolCall call, StorySession session) {
        AgentTool tool = tools.get(call.toolName());
        if (tool == null) {
            return ToolResult.error("There is no tool named " + call.toolName() + ". Available tools: "
                    + String.join(", ", tools.keySet()) + ".");
        }
        List<String> missing = new ArrayList<>();
        for (String name : required.get(tool.getName())) {
            if (call.argument(name).isEmpty()) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            return ToolResult.error(tool.getName() + " needs a value for: " + String.join(", ", missing) + ".");
        }
        try {
            return tool.execute(call.arguments(), session);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private static List<String> requiredArguments(AgentTool tool) {
        JsonNode schema;
        try {
            schema = JSON.readTree(tool.getSchema());
        } catch (Exception e) {
            throw new IllegalArgumentException("The schema of tool " + tool.getName() + " is not valid JSON.", e);
        }
        List<String> names = new ArrayList<>();
        schema.path("required").forEach(n -> names.add(n.asText()));
        return names;
    }
}
