package ca.yorku.storyteller.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.llm.FakeLLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMRequest;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.model.EventStatus;
import ca.yorku.storyteller.model.PivotalEvent;
import ca.yorku.storyteller.model.Plot;
import ca.yorku.storyteller.model.Scenario;
import ca.yorku.storyteller.model.StorySession;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Planning the hidden pivotal events (F05) and the per-turn guidance, with FakeLLMClient only. */
class PlotPlannerTest {

    private FakeLLMClient llm;
    private PlotPlanner planner;
    private final Scenario scenario = new Scenario("Ashfall", "A courier carries a forbidden letter.", "grim",
            "A city under grey snow", "");

    @BeforeEach
    void setUp() {
        llm = new FakeLLMClient();
        planner = new PlotPlanner(llm);
    }

    @Test
    void createPlotTurnsTheReplyIntoPendingEventsInOrder() {
        llm.enqueue(LLMResponse.ofText("{\"events\": [\"The letter is stolen.\", \"The guild burns.\"]}"));
        Plot plot = planner.createPlot(scenario, "  a betrayal  ");
        assertEquals("a betrayal", plot.getBasis());
        assertEquals(List.of("E1", "E2"), plot.getEvents().stream().map(PivotalEvent::getId).toList());
        assertEquals("The letter is stolen.", plot.getEvents().get(0).getDescription());
        assertTrue(plot.getEvents().stream().allMatch(e -> e.getStatus() == EventStatus.PENDING));
    }

    @Test
    void theRequestCarriesTheScenarioAndTheBasisOrSaysThereIsNone() {
        llm.enqueue(LLMResponse.ofText("{\"events\": [\"A\"]}")).enqueue(LLMResponse.ofText("{\"events\": [\"A\"]}"));
        planner.createPlot(scenario, "a betrayal");
        planner.createPlot(scenario, "");
        LLMRequest withBasis = llm.getRequests().get(0);
        String first = withBasis.getMessages().get(0).getText();
        assertTrue(first.contains("Premise: A courier carries a forbidden letter."));
        assertTrue(first.endsWith("## Plot basis from the user\na betrayal"));
        assertTrue(llm.getRequests().get(1).getMessages().get(0).getText().endsWith("None. Invent the plot from the scenario."));
        assertTrue(withBasis.getSystemPrompt().contains("JSON only"));
        assertTrue(withBasis.getTools().isEmpty());
    }

    @Test
    void textAroundTheJsonAndObjectEventsAreAccepted() {
        assertEquals(List.of("A", "B"), PlotPlanner.parseEvents(
                "Here is the plan:\n```json\n{\"events\": [\"A\", {\"description\": \"B\"}, \"  \"]}\n```"));
    }

    @Test
    void unreadableRepliesGiveNoEvents() {
        assertTrue(PlotPlanner.parseEvents("no json here").isEmpty());
        assertTrue(PlotPlanner.parseEvents("{\"events\": [oops}").isEmpty());
        assertTrue(PlotPlanner.parseEvents("{\"beats\": [\"A\"]}").isEmpty());
    }

    @Test
    void noEventsIsTreatedAsAFailedCall() {
        llm.enqueue(LLMResponse.ofText("I cannot think of anything."));
        LLMException e = assertThrows(LLMException.class, () -> planner.createPlot(scenario, ""));
        assertTrue(e.getMessage().contains("did not return any pivotal events"));
    }

    @Test
    void aRefusalOrFailureIsAnError() {
        llm.enqueue(LLMResponse.ofRefusal()).enqueueFailure(new LLMException("Could not reach Claude."));
        assertTrue(assertThrows(LLMException.class, () -> planner.createPlot(scenario, "")).getMessage().contains("declined"));
        assertEquals("Could not reach Claude.", assertThrows(LLMException.class, () -> planner.createPlot(scenario, "")).getMessage());
    }

    @Test
    void aLongPlanIsCut() {
        StringBuilder json = new StringBuilder("{\"events\": [");
        for (int i = 0; i < 12; i++) {
            json.append(i == 0 ? "" : ",").append("\"event ").append(i).append('"');
        }
        llm.enqueue(LLMResponse.ofText(json + "]}"));
        assertEquals(PlotPlanner.MAX_EVENTS, planner.createPlot(scenario, "").getEvents().size());
    }

    @Test
    void planStepListsThePendingEventsInOrder() {
        StorySession s = new StorySession("s");
        Plot plot = new Plot("", List.of(new PivotalEvent("E1", "A"), new PivotalEvent("E2", "B"),
                new PivotalEvent("E3", "C")));
        s.setPlot(plot);
        String step = planner.planStep(s, "x");
        assertTrue(step.contains("Next pivotal event (E1): A\nLater pivotal events: E2: B; E3: C"));
        assertTrue(step.contains("update_plot with action \"revise\""));
    }

    @Test
    void planStepNamesTheNextPendingEvent() {
        StorySession s = new StorySession("s");
        assertEquals("", planner.planStep(s, "x"));
        Plot plot = new Plot("", List.of(new PivotalEvent("E1", "A"), new PivotalEvent("E2", "B")));
        plot.markReached("E1");
        s.setPlot(plot);
        assertTrue(planner.planStep(s, "x").startsWith("Pivotal events reached so far: 1. Next pivotal event (E2): B"));
        assertTrue(planner.planStep(s, "x").contains("call update_plot with action \"reached\" and event_id \"E2\""));
        assertFalse(planner.planStep(s, "x").contains("Later pivotal events"), "E2 is the only one left");
        plot.markReached("E2");
        assertTrue(planner.planStep(s, "x").startsWith("Every pivotal event has been reached."));
        assertTrue(llm.getRequests().isEmpty(), "planStep makes no model call");
    }

    @Test
    void readyToEndOnceNoEventIsPendingAndOneWasReached() {
        assertFalse(planner.readyToEnd(null), "no plan, nothing to end on");
        Plot plot = new Plot("", List.of(new PivotalEvent("E1", "A"), new PivotalEvent("E2", "B")));
        assertFalse(planner.readyToEnd(plot));
        plot.markReached("E1");
        assertFalse(planner.readyToEnd(plot));
        plot.replacePending(List.of(new PivotalEvent("E3", "C")));
        assertFalse(planner.readyToEnd(plot), "E2 was dropped, but E3 is pending");
        plot.markReached("E3");
        assertTrue(planner.readyToEnd(plot));
    }
}
