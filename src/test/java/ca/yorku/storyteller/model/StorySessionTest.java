package ca.yorku.storyteller.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.ui.StoryObserver;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link StorySession} as the Observer subject, and its turns, proposals and summary. */
class StorySessionTest {

    /** Records every notification it receives. */
    private static class RecordingObserver implements StoryObserver {
        final List<StorySession> seen = new ArrayList<>();

        @Override
        public void onStoryChanged(StorySession session) {
            seen.add(session);
        }
    }

    private StorySession session;

    @BeforeEach
    void setUp() {
        session = new StorySession("s1");
    }

    private static Turn turn(int n, String narration) {
        return new Turn(n, "Do", "I look around.", narration, List.of(), List.of());
    }

    @Test
    void notifyObserversCallsEveryAttachedObserverOnce() {
        RecordingObserver gui = new RecordingObserver();
        RecordingObserver cli = new RecordingObserver();
        session.attach(gui);
        session.attach(cli);
        session.attach(gui);
        session.notifyObservers();
        assertEquals(1, gui.seen.size());
        assertEquals(1, cli.seen.size());
        assertSame(session, gui.seen.get(0));
    }

    @Test
    void detachedObserverIsNotNotified() {
        RecordingObserver o = new RecordingObserver();
        session.attach(o);
        session.detach(o);
        session.notifyObservers();
        assertTrue(o.seen.isEmpty());
    }

    @Test
    void observerMayDetachWhileBeingNotified() {
        StoryObserver selfRemoving = new StoryObserver() {
            @Override
            public void onStoryChanged(StorySession s) {
                s.detach(this);
            }
        };
        RecordingObserver other = new RecordingObserver();
        session.attach(selfRemoving);
        session.attach(other);
        session.notifyObservers();
        assertEquals(1, other.seen.size());
    }

    @Test
    void turnsAreAddedAndRemovedFromTheEnd() {
        assertTrue(session.lastTurn().isEmpty());
        session.addTurn(turn(1, "The square is empty."));
        session.addTurn(turn(2, "A bell rings."));
        assertEquals(3, session.nextTurnNumber());
        assertEquals("A bell rings.", session.lastTurn().orElseThrow().getNarration());
        assertEquals(2, session.removeLastTurn().getNumber());
        assertEquals(1, session.getTurns().size());
    }

    @Test
    void removeLastTurnOnEmptySessionFails() {
        assertThrows(IllegalStateException.class, session::removeLastTurn);
    }

    @Test
    void removingASummarisedTurnPullsTheSummaryMarkBack() {
        session.addTurn(turn(1, "One."));
        session.addTurn(turn(2, "Two."));
        session.setSummary("Things happened.", 2);
        session.removeLastTurn();
        assertEquals(1, session.getSummarizedUpTo());
        assertThrows(IllegalArgumentException.class, () -> session.setSummary("x", 5));
    }

    @Test
    void turnNarrationCannotBeEmpty() {
        assertThrows(IllegalArgumentException.class, () -> turn(1, " "));
        Turn t = turn(1, "Fine.");
        assertThrows(IllegalArgumentException.class, () -> t.setNarration(""));
    }

    @Test
    void approvingAProposalAddsItToTheLoreBook() {
        session.addProposal(new LoreProposal(null, "The Ash Bell", "Rings when the volcano wakes."));
        assertEquals("P1", session.getPendingProposals().get(0).getId());
        LoreEntry entry = session.approveProposal("p1");
        assertEquals("The Ash Bell", entry.getTitle());
        assertEquals(1, session.getLoreBook().size());
        assertTrue(session.getPendingProposals().isEmpty());
        assertEquals(ProposalStatus.APPROVED, session.getProposals().get(0).getStatus());
    }

    @Test
    void rejectedProposalNeverReachesTheLoreBook() {
        session.addProposal(new LoreProposal(null, "Rumour", "Probably false."));
        session.rejectProposal("P1");
        assertEquals(0, session.getLoreBook().size());
        assertEquals(ProposalStatus.REJECTED, session.getProposals().get(0).getStatus());
        assertThrows(IllegalArgumentException.class, () -> session.approveProposal("P1"));
    }

    @Test
    void proposalWithClashingTitleStaysPending() {
        session.getLoreBook().add(new LoreEntry(null, "The Ash Bell", "Already known.", List.of(), false));
        session.addProposal(new LoreProposal(null, "the ash bell", "Duplicate."));
        assertThrows(IllegalArgumentException.class, () -> session.approveProposal("P1"));
        assertEquals(1, session.getPendingProposals().size());
    }

    @Test
    void scenarioDescribeSkipsEmptyFields() {
        Scenario s = new Scenario("Ashfall", "A city under grey snow.", "", "Volcanic north", null);
        session.setScenario(s);
        assertEquals("Title: Ashfall\nPremise: A city under grey snow.\nSetting: Volcanic north",
                session.getScenario().describe());
    }
}
