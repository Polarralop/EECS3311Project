package ca.yorku.storyteller.agent.tools;

import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.StorySession;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lets the model search the lore book during a turn. Pinned entries are marked as canon.
 *
 * <p>Features: F04.
 */
public class SearchLoreTool implements AgentTool {

    /** At most this many entries go back to the model per search, to keep the context small. */
    static final int MAX_RESULTS = 5;

    @Override
    public String getName() {
        return "search_lore";
    }

    @Override
    public String getDescription() {
        return "Search the story's lore book for places, factions, history, rules and other facts. "
                + "Use it before narrating anything that may already be established. Entries marked "
                + "[canon] are pinned by the user and must not be contradicted.";
    }

    @Override
    public String getSchema() {
        return """
                {"type": "object",
                 "properties": {"query": {"type": "string", "description": "A few keywords, for example a name or a place."}},
                 "required": ["query"]}""";
    }

    /**
     * Entries matching every word of the query, pinned first. If none match every word, entries that
     * match any one word are returned instead, since the model often searches with several keywords.
     */
    @Override
    public ToolResult execute(Map<String, String> args, StorySession session) {
        String query = args.getOrDefault("query", "").strip();
        List<LoreEntry> found = session.getLoreBook().search(query);
        if (found.isEmpty()) {
            Set<LoreEntry> any = new LinkedHashSet<>();
            for (String word : query.split("\\s+")) {
                any.addAll(session.getLoreBook().search(word));
            }
            found = List.copyOf(any);
        }
        if (found.isEmpty()) {
            return ToolResult.ok("No lore entries match \"" + query + "\". Continue without them.");
        }
        List<LoreEntry> shown = found.stream().limit(MAX_RESULTS).toList();
        StringBuilder sb = new StringBuilder();
        for (LoreEntry e : shown) {
            sb.append(e.getId()).append(e.isPinned() ? " [canon] " : " ").append(e.getTitle());
            if (!e.getTags().isEmpty()) {
                sb.append(" (").append(String.join(", ", e.getTags())).append(')');
            }
            sb.append(": ").append(e.getContent()).append('\n');
        }
        if (found.size() > shown.size()) {
            sb.append("(").append(found.size() - shown.size()).append(" more; search with more specific words.)");
        }
        return ToolResult.ok(sb.toString().strip(), shown.stream().map(LoreEntry::getId).toList());
    }
}
