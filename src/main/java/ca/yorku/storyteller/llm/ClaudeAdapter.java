package ca.yorku.storyteller.llm;

/**
 * Translates LLMRequest/LLMResponse to and from Anthropic's Messages API (the adaptee, via the anthropic-java SDK).
 *
 * <p>Features: F01 F04-F09 F12.
 * <p>Patterns: Adapter (Adapter).
 * <p>TODO (Stage 1 design): generate(request), toApiBody(request), fromApiBody(json).
 */
public class ClaudeAdapter implements LLMClient {
}
