package ca.yorku.storyteller.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.ProposalStatus;
import ca.yorku.storyteller.model.StorySession;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link ProposeLoreTool} queues proposals and never touches the lore book (F07). */
class ProposeLoreToolTest {

    private ToolRegistry registry;
    private StorySession session;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.register(new ProposeLoreTool());
        session = new StorySession("s");
        session.getLoreBook().add(new LoreEntry(null, "Ashfall", "A city.", List.of(), false));
    }

    private ToolResult propose(String title, String content) {
        return registry.execute(new ToolCall("p", "propose_lore", Map.of("title", title, "content", content)), session);
    }

    @Test
    void aValidProposalIsQueuedAsPendingAndTheLoreBookIsUntouched() {
        ToolResult r = propose(" The Ember Guild ", "Smiths who keep the city's forges lit.");
        assertTrue(r.success());
        assertTrue(r.content().startsWith("Proposal P1 is waiting for the user's review."));
        assertEquals(1, session.getPendingProposals().size());
        assertEquals("The Ember Guild", session.getPendingProposals().get(0).getTitle());
        assertEquals(ProposalStatus.PENDING, session.getPendingProposals().get(0).getStatus());
        assertEquals(1, session.getLoreBook().size());
    }

    @Test
    void emptyTitleOrContentIsRejectedAndNothingIsQueued() {
        assertFalse(propose("", "content").success());
        assertFalse(propose("Title", "  ").success());
        assertTrue(session.getProposals().isEmpty());
    }

    @Test
    void aTitleAlreadyInTheLoreBookIsRejected() {
        ToolResult r = propose("ashfall", "Another city.");
        assertFalse(r.success());
        assertTrue(r.content().contains("already has an entry titled \"Ashfall\" (L1)"));
        assertTrue(session.getProposals().isEmpty());
    }

    @Test
    void theSameTitleCannotWaitTwice() {
        propose("The Ember Guild", "Smiths.");
        ToolResult again = propose("the ember guild", "Smiths again.");
        assertFalse(again.success());
        assertEquals(1, session.getProposals().size());
    }

    @Test
    void aRejectedTitleCanBeProposedAgain() {
        propose("The Ember Guild", "Smiths.");
        session.rejectProposal("P1");
        assertTrue(propose("The Ember Guild", "Smiths, better worded.").success());
        assertEquals("P2", session.getPendingProposals().get(0).getId());
    }
}
