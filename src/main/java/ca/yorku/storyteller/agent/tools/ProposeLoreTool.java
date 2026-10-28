package ca.yorku.storyteller.agent.tools;

import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.LoreProposal;
import ca.yorku.storyteller.model.StorySession;
import java.util.Map;

/**
 * Lets the model propose a new lore entry for the user to review. The proposal is only queued; it
 * reaches the lore book when the user approves it through StoryController.resolveProposal.
 *
 * <p>Features: F07.
 */
public class ProposeLoreTool implements AgentTool {

    @Override
    public String getName() {
        return "propose_lore";
    }

    @Override
    public String getDescription() {
        return "Propose a new lore entry for a lasting fact your narration just established (a named place, "
                + "faction, object, custom or piece of history) that is not in the lore book yet. The user "
                + "approves or rejects it; until then it is not canon. Do not propose facts about the user's "
                + "character, and propose at most one or two per turn.";
    }

    @Override
    public String getSchema() {
        return """
                {"type": "object",
                 "properties": {
                   "title": {"type": "string", "description": "A short name for the fact, for example \\"The Ember Guild\\"."},
                   "content": {"type": "string", "description": "One to three sentences stating the fact."}},
                 "required": ["title", "content"]}""";
    }

    /**
     * Queues the proposal as pending. Refused when the lore book already has an entry with that title or
     * the same title is already waiting for review.
     */
    @Override
    public ToolResult execute(Map<String, String> args, StorySession session) {
        String title = args.getOrDefault("title", "").strip();
        String content = args.getOrDefault("content", "").strip();
        for (LoreEntry e : session.getLoreBook().getAll()) {
            if (e.getTitle().equalsIgnoreCase(title)) {
                return ToolResult.error("The lore book already has an entry titled \"" + e.getTitle()
                        + "\" (" + e.getId() + "). Use search_lore to read it instead.");
            }
        }
        for (LoreProposal p : session.getPendingProposals()) {
            if (p.getTitle().equalsIgnoreCase(title)) {
                return ToolResult.error("\"" + p.getTitle() + "\" is already waiting for the user's review.");
            }
        }
        LoreProposal proposal = new LoreProposal(null, title, content);
        session.addProposal(proposal);
        return ToolResult.ok("Proposal " + proposal.getId() + " is waiting for the user's review. Do not treat it "
                + "as canon until it is approved.");
    }
}
