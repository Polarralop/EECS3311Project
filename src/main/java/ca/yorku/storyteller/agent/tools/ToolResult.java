package ca.yorku.storyteller.agent.tools;

import java.util.List;

/**
 * The result of running a tool, sent back to the model.
 *
 * <p>Features: F04.
 *
 * @param success true when the tool ran; false when it refused the call (the model sees why)
 * @param content what the tool returned, or the reason it refused
 * @param loreIds the lore entries the result showed the model, so the turn can list the lore it used
 */
public record ToolResult(boolean success, String content, List<String> loreIds) {

    public ToolResult {
        content = content == null ? "" : content;
        loreIds = loreIds == null ? List.of() : List.copyOf(loreIds);
    }

    public static ToolResult ok(String content) {
        return new ToolResult(true, content, List.of());
    }

    public static ToolResult ok(String content, List<String> loreIds) {
        return new ToolResult(true, content, loreIds);
    }

    public static ToolResult error(String reason) {
        return new ToolResult(false, reason, List.of());
    }
}
