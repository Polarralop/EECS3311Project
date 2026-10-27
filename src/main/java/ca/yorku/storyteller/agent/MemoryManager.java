package ca.yorku.storyteller.agent;

import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import java.util.List;

/**
 * Builds the model context and summarizes older turns so long stories stay coherent.
 *
 * <p>Features: F04 F09 F11.
 * <p>Patterns: Facade (Subsystem).
 * <p>TODO (F09, week 3): summarizeIfNeeded(), recap().
 */
public class MemoryManager {

    /** Turns sent to the model word for word; older ones are covered by the summary (F09). */
    public static final int DEFAULT_RECENT_TURN_LIMIT = 8;

    private final int recentTurnLimit;

    public MemoryManager() {
        this(DEFAULT_RECENT_TURN_LIMIT);
    }

    /**
     * @throws IllegalArgumentException if the limit is below 1
     */
    public MemoryManager(int recentTurnLimit) {
        if (recentTurnLimit < 1) {
            throw new IllegalArgumentException("The recent-turn limit must be at least 1.");
        }
        this.recentTurnLimit = recentTurnLimit;
    }

    public int getRecentTurnLimit() {
        return recentTurnLimit;
    }

    /**
     * The story so far for the next request: the stored summary (if any) followed by the most recent
     * turns, up to the limit. Empty before the first turn.
     */
    public String buildContext(StorySession session) {
        StringBuilder sb = new StringBuilder();
        if (!session.getSummary().isBlank()) {
            sb.append("Summary of earlier events:\n").append(session.getSummary().strip()).append("\n\n");
        }
        List<Turn> turns = session.getTurns();
        List<Turn> recent = turns.subList(Math.max(0, turns.size() - recentTurnLimit), turns.size());
        for (Turn t : recent) {
            sb.append("Turn ").append(t.getNumber()).append(" (").append(t.getModeName()).append("): ")
                    .append(t.getUserInput().strip()).append('\n')
                    .append(t.getNarration().strip()).append("\n\n");
        }
        return sb.toString().strip();
    }
}
