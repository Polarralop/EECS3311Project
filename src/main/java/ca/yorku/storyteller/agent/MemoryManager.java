package ca.yorku.storyteller.agent;

import ca.yorku.storyteller.llm.LLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMMessage;
import ca.yorku.storyteller.llm.LLMRequest;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import java.util.List;

/**
 * Builds the model context and summarizes older turns so long stories stay coherent.
 *
 * <p>Features: F04 F09 F11.
 * <p>Patterns: Facade (Subsystem).
 * <p>TODO (F09, week 3): recap().
 */
public class MemoryManager {

    /** Turns sent to the model word for word; older ones are covered by the summary (F09). */
    public static final int DEFAULT_RECENT_TURN_LIMIT = 8;

    private final LLMClient llm;
    private final int recentTurnLimit;
    private final String summarizerPrompt;

    public MemoryManager(LLMClient llm) {
        this(llm, DEFAULT_RECENT_TURN_LIMIT);
    }

    /**
     * @throws IllegalArgumentException if the limit is below 1
     */
    public MemoryManager(LLMClient llm, int recentTurnLimit) {
        if (recentTurnLimit < 1) {
            throw new IllegalArgumentException("The recent-turn limit must be at least 1.");
        }
        this.llm = llm;
        this.recentTurnLimit = recentTurnLimit;
        this.summarizerPrompt = StoryAgent.loadPrompt("summarizer");
    }

    public int getRecentTurnLimit() {
        return recentTurnLimit;
    }

    /**
     * The story so far for the next request: the stored summary (if any) followed by the turns it does
     * not cover, word for word. That is normally the most recent turns up to the limit; if summarizing
     * failed, the uncovered older turns are sent too (at most twice the limit) so nothing drops out of
     * the context. Empty before the first turn.
     */
    public String buildContext(StorySession session) {
        StringBuilder sb = new StringBuilder();
        if (!session.getSummary().isBlank()) {
            sb.append("Summary of earlier events:\n").append(session.getSummary().strip()).append("\n\n");
        }
        List<Turn> turns = session.getTurns();
        int start = Math.min(session.getSummarizedUpTo(), turns.size() - recentTurnLimit);
        start = Math.max(start, turns.size() - 2 * recentTurnLimit);
        sb.append(describe(turns.subList(Math.max(0, start), turns.size())));
        return sb.toString().strip();
    }

    /**
     * Folds the turns that have fallen out of the recent window into the stored summary (F09). Does
     * nothing while the story is still short or the summary already reaches the window. If Claude
     * fails or declines, the old summary is kept and the step is tried again on the next turn.
     *
     * @return true if the summary was updated
     */
    public boolean summarizeIfNeeded(StorySession session) {
        List<Turn> turns = session.getTurns();
        int upTo = turns.size() - recentTurnLimit;
        int from = session.getSummarizedUpTo();
        if (upTo <= from) {
            return false;
        }
        String message = "## Summary so far\n"
                + (session.getSummary().isBlank() ? "None yet: this is the start of the story." : session.getSummary().strip())
                + "\n\n## Turns to add\n" + describe(turns.subList(from, upTo));
        LLMResponse reply;
        try {
            reply = llm.generate(new LLMRequest(summarizerPrompt, List.of(LLMMessage.user(message)), List.of(), 2_000));
        } catch (LLMException e) {
            return false;
        }
        if (reply.isRefused() || reply.getText().isBlank()) {
            return false;
        }
        session.setSummary(reply.getText().strip(), upTo);
        return true;
    }

    private static String describe(List<Turn> turns) {
        StringBuilder sb = new StringBuilder();
        for (Turn t : turns) {
            sb.append("Turn ").append(t.getNumber()).append(" (").append(t.getModeName()).append("): ")
                    .append(t.getUserInput().strip()).append('\n')
                    .append(t.getNarration().strip()).append("\n\n");
        }
        return sb.toString().strip();
    }
}
