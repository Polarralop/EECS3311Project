package ca.yorku.storyteller.llm;

import java.util.List;

/**
 * One message in a conversation with the model, without any provider types.
 *
 * <p>A user message carries text or tool results; an assistant message carries text and tool calls.
 * {@code providerContent} lets an adapter keep its own exact copy of an assistant reply (for example
 * thinking blocks that must be sent back unchanged). Only the adapter that produced it reads it;
 * everyone else treats it as opaque.
 */
public final class LLMMessage {

    /** Who sent the message. */
    public enum Role { USER, ASSISTANT }

    private final Role role;
    private final String text;
    private final List<LLMToolCall> toolCalls;
    private final List<LLMToolResult> toolResults;
    private final Object providerContent;

    private LLMMessage(Role role, String text, List<LLMToolCall> toolCalls, List<LLMToolResult> toolResults,
                       Object providerContent) {
        this.role = role;
        this.text = text == null ? "" : text;
        this.toolCalls = List.copyOf(toolCalls);
        this.toolResults = List.copyOf(toolResults);
        this.providerContent = providerContent;
    }

    public static LLMMessage user(String text) {
        return new LLMMessage(Role.USER, text, List.of(), List.of(), null);
    }

    /** The user turn that answers the assistant's tool calls. */
    public static LLMMessage toolResults(List<LLMToolResult> results) {
        if (results.isEmpty()) {
            throw new IllegalArgumentException("A tool result message needs at least one result.");
        }
        return new LLMMessage(Role.USER, "", List.of(), results, null);
    }

    public static LLMMessage assistant(String text, List<LLMToolCall> toolCalls, Object providerContent) {
        return new LLMMessage(Role.ASSISTANT, text, toolCalls, List.of(), providerContent);
    }

    public Role getRole() {
        return role;
    }

    public String getText() {
        return text;
    }

    public List<LLMToolCall> getToolCalls() {
        return toolCalls;
    }

    public List<LLMToolResult> getToolResults() {
        return toolResults;
    }

    public Object getProviderContent() {
        return providerContent;
    }
}
