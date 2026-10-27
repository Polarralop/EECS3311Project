package ca.yorku.storyteller.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.agent.DoModeStrategy;
import ca.yorku.storyteller.agent.StoryAgent;
import ca.yorku.storyteller.llm.FakeLLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.model.EventStatus;
import ca.yorku.storyteller.model.PivotalEvent;
import ca.yorku.storyteller.model.Plot;
import ca.yorku.storyteller.model.StorySession;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link SubmitTurnCommand} on its own: what execute changes and what undo puts back. */
class SubmitTurnCommandTest {

    private final FakeLLMClient llm = new FakeLLMClient();
    private final StoryAgent agent = StoryAgent.create(llm);

    @Test
    void undoRestoresTheExactPlotFromBeforeTheTurn() {
        StorySession s = new StorySession("s");
        s.setPlot(new Plot("", List.of(new PivotalEvent("E1", "A"))));
        llm.enqueue(LLMResponse.ofText("Narration."));
        SubmitTurnCommand c = new SubmitTurnCommand(s, agent, "go", new DoModeStrategy());
        c.execute();
        s.getPlot().markReached("E1");
        c.undo();
        assertTrue(s.getTurns().isEmpty());
        assertEquals(EventStatus.PENDING, s.getPlot().getEvents().get(0).getStatus());
        assertEquals("go", c.getInput());
        assertEquals("Do", c.getMode().getName());
    }

    @Test
    void aStoryWithoutAPlanStaysWithoutOne() {
        StorySession s = new StorySession("s");
        llm.enqueue(LLMResponse.ofText("Narration."));
        SubmitTurnCommand c = new SubmitTurnCommand(s, agent, "go", new DoModeStrategy());
        c.execute();
        c.undo();
        assertNull(s.getPlot());
    }

    @Test
    void aFailedTurnKeepsThePlotAndAddsNoTurn() {
        StorySession s = new StorySession("s");
        Plot plot = new Plot("", List.of(new PivotalEvent("E1", "A")));
        s.setPlot(plot);
        llm.enqueueFailure(new LLMException("down"));
        SubmitTurnCommand c = new SubmitTurnCommand(s, agent, "go", new DoModeStrategy());
        assertThrows(LLMException.class, c::execute);
        assertTrue(s.getTurns().isEmpty());
        assertEquals(1, s.getPlot().getEvents().size());
        assertNull(c.getResult());
    }
}
