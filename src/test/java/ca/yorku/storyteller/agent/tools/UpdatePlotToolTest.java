package ca.yorku.storyteller.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.model.EventStatus;
import ca.yorku.storyteller.model.PivotalEvent;
import ca.yorku.storyteller.model.Plot;
import ca.yorku.storyteller.model.StorySession;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link UpdatePlotTool} marks events reached and revises the pending ones, validated (F06). */
class UpdatePlotToolTest {

    private ToolRegistry registry;
    private StorySession session;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.register(new UpdatePlotTool());
        session = new StorySession("s");
        session.setPlot(new Plot("", List.of(new PivotalEvent("E1", "The letter is stolen."),
                new PivotalEvent("E2", "The guild burns."), new PivotalEvent("E3", "The thief is unmasked."))));
    }

    private ToolResult run(Map<String, String> args) {
        return registry.execute(new ToolCall("u", "update_plot", args), session);
    }

    private List<EventStatus> statuses() {
        return session.getPlot().getEvents().stream().map(PivotalEvent::getStatus).toList();
    }

    @Test
    void reachedMarksTheEventAndNamesTheNextOne() {
        ToolResult r = run(Map.of("action", "reached", "event_id", "e1"));
        assertTrue(r.success());
        assertEquals(List.of(EventStatus.REACHED, EventStatus.PENDING, EventStatus.PENDING), statuses());
        assertTrue(r.content().contains("Next pivotal event (E2): The guild burns."));
    }

    @Test
    void reachingALaterEventFirstIsAllowedAndSaysWhatIsStillPending() {
        ToolResult r = run(Map.of("action", "reached", "event_id", "E2"));
        assertTrue(r.success());
        assertTrue(r.content().contains("ahead of E1"));
        assertEquals(List.of(EventStatus.PENDING, EventStatus.REACHED, EventStatus.PENDING), statuses());
    }

    @Test
    void reachingTheLastEventSaysTheStoryCanEnd() {
        run(Map.of("action", "reached", "event_id", "E1"));
        run(Map.of("action", "reached", "event_id", "E2"));
        ToolResult r = run(Map.of("action", "reached", "event_id", "E3"));
        assertTrue(r.content().contains("Every pivotal event has been reached"));
        assertTrue(session.getPlot().isComplete());
    }

    @Test
    void anUnknownOrAlreadyReachedEventIsRefusedAndThePlotIsUnchanged() {
        assertFalse(run(Map.of("action", "reached", "event_id", "E9")).success());
        run(Map.of("action", "reached", "event_id", "E1"));
        ToolResult again = run(Map.of("action", "reached", "event_id", "E1"));
        assertFalse(again.success());
        assertEquals("There is no pending pivotal event E1.", again.content());
        assertFalse(run(Map.of("action", "reached")).success(), "event_id is needed");
        assertEquals(List.of(EventStatus.REACHED, EventStatus.PENDING, EventStatus.PENDING), statuses());
    }

    @Test
    void reviseDropsThePendingEventsAndAppendsNewOnesWithFreshIds() {
        run(Map.of("action", "reached", "event_id", "E1"));
        ToolResult r = run(Map.of("action", "revise",
                "new_events", "[\"The courier joins the thieves.\", \"The guild hunts them both.\"]"));
        assertTrue(r.success(), r.content());
        List<PivotalEvent> events = session.getPlot().getEvents();
        assertEquals(5, events.size());
        assertEquals(List.of(EventStatus.REACHED, EventStatus.DROPPED, EventStatus.DROPPED,
                EventStatus.PENDING, EventStatus.PENDING), statuses());
        assertEquals("E4", events.get(3).getId());
        assertEquals("The guild hunts them both.", events.get(4).getDescription());
        assertTrue(r.content().contains("Next pivotal event (E4): The courier joins the thieves."));
    }

    @Test
    void reviseAcceptsOneEventPerLineWhenTheArgumentIsNotJson() {
        assertEquals(List.of("First twist.", "Second twist."),
                UpdatePlotTool.parseEvents("1. First twist.\n\n- Second twist."));
        assertEquals(List.of("3 riders arrive."), UpdatePlotTool.parseEvents("3 riders arrive."));
    }

    @Test
    void anInvalidRevisionIsRefusedAndThePlotIsUnchanged() {
        assertFalse(run(Map.of("action", "revise")).success(), "no events");
        assertFalse(run(Map.of("action", "revise", "new_events", "[]")).success());
        assertFalse(run(Map.of("action", "revise",
                "new_events", "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\"]")).success(), "too many");
        assertFalse(run(Map.of("action", "skip")).success(), "unknown action");
        assertFalse(run(Map.of()).success(), "action is required");
        assertEquals(3, session.getPlot().getEvents().size());
        assertEquals(List.of(EventStatus.PENDING, EventStatus.PENDING, EventStatus.PENDING), statuses());
    }

    @Test
    void nothingCanBeRevisedOnceEveryEventIsReached() {
        for (String id : List.of("E1", "E2", "E3")) {
            run(Map.of("action", "reached", "event_id", id));
        }
        ToolResult r = run(Map.of("action", "revise", "new_events", "[\"More.\"]"));
        assertFalse(r.success());
        assertEquals(3, session.getPlot().getEvents().size());
    }

    @Test
    void aStoryWithoutAPlanIsRefused() {
        session.setPlot(null);
        assertFalse(run(Map.of("action", "reached", "event_id", "E1")).success());
    }
}
