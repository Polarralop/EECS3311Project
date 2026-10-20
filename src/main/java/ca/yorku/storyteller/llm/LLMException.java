package ca.yorku.storyteller.llm;

/**
 * A model call failed: no key, no network, a rate limit or an error from the provider.
 * The message is written for the user; the controller shows it as an error.
 *
 * <p>Features: F01 F04-F09 F12.
 */
public class LLMException extends RuntimeException {

    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
