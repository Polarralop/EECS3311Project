package ca.yorku.storyteller.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.model.EventStatus;
import ca.yorku.storyteller.model.PivotalEvent;
import ca.yorku.storyteller.model.Plot;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Context building in {@link MemoryManager} and the steering text from {@link PlotPlanner#planStep}. */
class MemoryManagerTest {

    private static StorySession withTurns(int n) {
        StorySession s = new StorySession("s");
        for (int i = 1; i <= n; i++) {
            s.addTurn(new Turn(i, "Do", "input " + i, "narration " + i, List.of(), List.of()));
        }
        return s;
    }

    @Test
    void emptyBeforeTheFirstTurn() {
        assertEquals("", new MemoryManager().buildContext(new StorySession("s")));
    }

    @Test
    void onlyTheRecentTurnsAreSentWordForWord() {
        String c = new MemoryManager(2).buildContext(withTurns(4));
        assertFalse(c.contains("narration 2"));
        assertTrue(c.startsWith("Turn 3 (Do): input 3\nnarration 3"));
        assertTrue(c.endsWith("narration 4"));
    }

    @Test
    void theSummaryComesFirst() {
        StorySession s = withTurns(3);
        s.setSummary("Mira reached the city.", 2);
        String c = new MemoryManager(1).buildContext(s);
        assertTrue(c.startsWith("Summary of earlier events:\nMira reached the city.\n\nTurn 3"));
    }

    @Test
    void theLimitMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryManager(0));
    }

    @Test
    void planStepNamesOnlyTheNextPendingEvent() {
        StorySession s = new StorySession("s");
        assertEquals("", new PlotPlanner().planStep(s, "x"));
        Plot plot = new Plot("", List.of(new PivotalEvent("E1", "A"), new PivotalEvent("E2", "B")));
        plot.markReached("E1");
        s.setPlot(plot);
        String g = new PlotPlanner().planStep(s, "x");
        assertTrue(g.startsWith("Pivotal events reached so far: 1. Next pivotal event (E2): B"));
        plot.markReached("E2");
        assertEquals(EventStatus.REACHED, plot.getEvents().get(1).getStatus());
        assertTrue(new PlotPlanner().planStep(s, "x").startsWith("Every pivotal event has been reached."));
    }
}
