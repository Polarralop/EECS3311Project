package ca.yorku.storyteller.agent.tools;

import ca.yorku.storyteller.model.StorySession;
import java.util.Map;

/**
 * An operation the model may request. The app validates and executes it against the session.
 *
 * <p>Features: F04 F06 F07.
 */
public interface AgentTool {

    /** The name the model uses to call the tool, for example "search_lore". */
    String getName();

    /** Tells the model what the tool does and when to use it. */
    String getDescription();

    /** The tool's input as a JSON Schema object with "properties" and optionally "required". */
    String getSchema();

    /**
     * Runs the tool. {@link ToolRegistry} has already checked that every required argument is present.
     * A tool that refuses the request returns {@link ToolResult#error} or throws
     * {@link IllegalArgumentException}; either way the session is left unchanged.
     */
    ToolResult execute(Map<String, String> args, StorySession session);
}
