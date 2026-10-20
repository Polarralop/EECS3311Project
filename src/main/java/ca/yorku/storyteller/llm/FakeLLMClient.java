package ca.yorku.storyteller.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Scripted LLMClient for tests, so the agent can be tested without live, paid calls.
 * Responses are returned in the order they were enqueued, and every request is recorded.
 *
 * <p>Features: tests.
 * <p>Patterns: Adapter (second Target implementation).
 */
public class FakeLLMClient implements LLMClient {

    private final Queue<Object> scripted = new ArrayDeque<>();
    private final List<LLMRequest> requests = new ArrayList<>();

    /** Queues a response for the next call. */
    public FakeLLMClient enqueue(LLMResponse response) {
        scripted.add(response);
        return this;
    }

    /** Queues a failure for the next call. */
    public FakeLLMClient enqueueFailure(LLMException failure) {
        scripted.add(failure);
        return this;
    }

    @Override
    public LLMResponse generate(LLMRequest request) {
        requests.add(request);
        Object next = scripted.poll();
        if (next == null) {
            throw new IllegalStateException("FakeLLMClient has no scripted response left for request " + requests.size() + ".");
        }
        if (next instanceof LLMException failure) {
            throw failure;
        }
        return (LLMResponse) next;
    }

    /** Every request received so far, oldest first. */
    public List<LLMRequest> getRequests() {
        return List.copyOf(requests);
    }

    public int remaining() {
        return scripted.size();
    }
}
