package ca.yorku.storyteller.llm;

/**
 * The one operation the agent needs from a language model: generate a response to a request.
 *
 * <p>Features: F01 F04-F09 F12.
 * <p>Patterns: Adapter (Target).
 */
public interface LLMClient {

    /**
     * Sends one request and returns the model's reply. A tool-use loop calls this repeatedly,
     * appending the reply and the tool results to the request's messages each time.
     *
     * @throws LLMException if the call fails
     */
    LLMResponse generate(LLMRequest request);
}
