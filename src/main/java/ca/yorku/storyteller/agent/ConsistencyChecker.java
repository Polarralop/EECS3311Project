package ca.yorku.storyteller.agent;

import ca.yorku.storyteller.llm.LLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMMessage;
import ca.yorku.storyteller.llm.LLMRequest;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.StorySession;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flags narration that contradicts the pinned canon facts or the lore a turn used (F08). It warns and
 * never blocks: the warnings are stored on the turn and shown under its narration, and the user may
 * retry or edit the turn.
 *
 * <p>Features: F08.
 * <p>Patterns: Facade (Subsystem).
 */
public class ConsistencyChecker {

    /** More warnings than this for one turn are cut; the user only needs to see that the turn is off. */
    static final int MAX_WARNINGS = 5;

    /** Start of the warning added when the check itself could not run (UC04 8b). */
    public static final String NOT_CHECKED = "This turn was not checked against the lore";

    private final LLMClient llm;
    private final String prompt;

    public ConsistencyChecker(LLMClient llm) {
        this.llm = llm;
        this.prompt = StoryAgent.loadPrompt("consistency-check");
    }

    /**
     * Compares the narration with every pinned entry and the entries the turn looked up. Returns one
     * warning per contradiction, naming the entry, or an empty list. With no such lore the check is
     * skipped and no model call is made. If the call fails, Claude declines or the answer cannot be
     * read, the only warning says the turn was not checked.
     */
    public List<String> check(String narration, StorySession session, Collection<String> usedLoreIds) {
        Map<String, LoreEntry> entries = new LinkedHashMap<>();
        for (LoreEntry e : session.getLoreBook().getPinned()) {
            entries.put(e.getId(), e);
        }
        for (String id : usedLoreIds) {
            session.getLoreBook().find(id).ifPresent(e -> entries.putIfAbsent(e.getId(), e));
        }
        if (entries.isEmpty()) {
            return List.of();
        }
        StringBuilder message = new StringBuilder("## Lore\n");
        for (LoreEntry e : entries.values()) {
            message.append(e.getId()).append(e.isPinned() ? " [canon] " : " ").append(e.getTitle()).append(": ")
                    .append(e.getContent().strip()).append('\n');
        }
        message.append("\n## Passage to check\n").append(narration.strip());
        LLMResponse reply;
        try {
            reply = llm.generate(new LLMRequest(prompt, List.of(LLMMessage.user(message.toString())), List.of(), 2_000));
        } catch (LLMException e) {
            return List.of(NOT_CHECKED + ": " + e.getMessage());
        }
        if (reply.isRefused()) {
            return List.of(NOT_CHECKED + ": Claude declined to check it.");
        }
        List<String> warnings = parseWarnings(reply.getText(), entries);
        return warnings == null ? List.of(NOT_CHECKED + ": the check's answer could not be read.") : warnings;
    }

    /**
     * Reads {"contradictions": [{"entry": "L1", "problem": "..."}]} into warnings such as
     * {@code Contradicts L1 "Ashfall" (canon): ...}. Null when the reply has no such list.
     */
    static List<String> parseWarnings(String text, Map<String, LoreEntry> entries) {
        JsonNode root = StoryAgent.jsonObjectIn(text);
        if (root == null || !root.path("contradictions").isArray()) {
            return null;
        }
        List<String> warnings = new ArrayList<>();
        for (JsonNode c : root.path("contradictions")) {
            String problem = c.path("problem").asText("").strip();
            if (problem.isEmpty() || warnings.size() >= MAX_WARNINGS) {
                continue;
            }
            String id = c.path("entry").asText("").strip();
            LoreEntry e = entries.values().stream().filter(x -> x.getId().equalsIgnoreCase(id)).findFirst().orElse(null);
            String name = e == null ? (id.isEmpty() ? "the lore" : id)
                    : e.getId() + " \"" + e.getTitle() + "\"" + (e.isPinned() ? " (canon)" : "");
            warnings.add("Contradicts " + name + ": " + problem);
        }
        return warnings;
    }
}
