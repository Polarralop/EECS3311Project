package ca.yorku.storyteller.llm;

import java.util.List;

/**
 * Provider-neutral response: text, tool calls and token usage.
 *
 * <p>Features: F01 F04-F09 F12.
 */
public final class LLMResponse {

    private final String text;
    private final boolean refused;
    private final List<LLMToolCall> toolCalls;
    private final String stopReason;
    private final long inputTokens;
    private final long outputTokens;
    private final LLMMessage assistantMessage;

    public LLMResponse(String text, boolean refused, List<LLMToolCall> toolCalls, String stopReason,
                       long inputTokens, long outputTokens, LLMMessage assistantMessage) {
        this.text = text == null ? "" : text;
        this.refused = refused;
        this.toolCalls = List.copyOf(toolCalls);
        this.stopReason = stopReason == null ? "" : stopReason;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.assistantMessage = assistantMessage;
    }

    /** A plain text reply, as a scripted response for {@link FakeLLMClient}. */
    public static LLMResponse ofText(String text) {
        return new LLMResponse(text, false, List.of(), "end_turn", 0, 0,
                LLMMessage.assistant(text, List.of(), null));
    }

    /** A reply that asks for tools, as a scripted response for {@link FakeLLMClient}. */
    public static LLMResponse ofToolCalls(String text, List<LLMToolCall> calls) {
        return new LLMResponse(text, false, calls, "tool_use", 0, 0,
                LLMMessage.assistant(text, calls, null));
    }

    /** A declined request, as a scripted response for {@link FakeLLMClient}. */
    public static LLMResponse ofRefusal() {
        return new LLMResponse("", true, List.of(), "refusal", 0, 0, LLMMessage.assistant("", List.of(), null));
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /** All text blocks of the reply, joined. */
    public String getText() {
        return text;
    }

    /** True when the model declined the request (stop reason "refusal"). */
    public boolean isRefused() {
        return refused;
    }

    public List<LLMToolCall> getToolCalls() {
        return toolCalls;
    }

    public String getStopReason() {
        return stopReason;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    /** The reply as a message to append to the conversation before sending tool results. */
    public LLMMessage getAssistantMessage() {
        return assistantMessage;
    }
}
