package ca.yorku.storyteller.llm;

/**
 * The result of one tool call, sent back to the model in the next request.
 *
 * @param toolCallId the {@link LLMToolCall#id()} this answers
 * @param content    what the tool returned, or why it failed
 * @param error      true when the tool refused or failed
 */
public record LLMToolResult(String toolCallId, String content, boolean error) {
}
