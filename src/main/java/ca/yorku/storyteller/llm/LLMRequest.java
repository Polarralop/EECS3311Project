package ca.yorku.storyteller.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider-neutral request: system prompt, messages and tool definitions.
 *
 * <p>Features: F01 F04-F09 F12.
 */
public final class LLMRequest {

    /** Enough room for a long narration without hitting HTTP timeouts on a non-streaming call. */
    public static final long DEFAULT_MAX_TOKENS = 16_000;

    private final String systemPrompt;
    private final List<LLMMessage> messages;
    private final List<LLMToolSpec> tools;
    private final long maxTokens;

    public LLMRequest(String systemPrompt, List<LLMMessage> messages, List<LLMToolSpec> tools, long maxTokens) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("A request needs at least one message.");
        }
        if (messages.get(0).getRole() != LLMMessage.Role.USER) {
            throw new IllegalArgumentException("The first message must come from the user.");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive.");
        }
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.messages = List.copyOf(messages);
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.maxTokens = maxTokens;
    }

    public LLMRequest(String systemPrompt, List<LLMMessage> messages, List<LLMToolSpec> tools) {
        this(systemPrompt, messages, tools, DEFAULT_MAX_TOKENS);
    }

    /** A copy with more messages appended, for the next step of a tool-use loop. */
    public LLMRequest withMessages(LLMMessage... more) {
        List<LLMMessage> all = new ArrayList<>(messages);
        all.addAll(List.of(more));
        return new LLMRequest(systemPrompt, all, tools, maxTokens);
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<LLMMessage> getMessages() {
        return messages;
    }

    public List<LLMToolSpec> getTools() {
        return tools;
    }

    public long getMaxTokens() {
        return maxTokens;
    }
}
