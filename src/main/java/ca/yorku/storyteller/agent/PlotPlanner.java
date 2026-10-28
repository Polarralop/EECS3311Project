package ca.yorku.storyteller.agent;

import ca.yorku.storyteller.llm.LLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMMessage;
import ca.yorku.storyteller.llm.LLMRequest;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.model.PivotalEvent;
import ca.yorku.storyteller.model.Plot;
import ca.yorku.storyteller.model.Scenario;
import ca.yorku.storyteller.model.StorySession;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Creates the pivotal events and decides each turn whether to steer back or revise the plan.
 *
 * <p>Features: F05 F06 F12.
 * <p>Patterns: Facade (Subsystem).
 * <p>TODO (Stage 1 design): readyToEnd() (F12).
 */
public class PlotPlanner {

    /** A plan longer than this is cut, so a long answer cannot make the story drag on. */
    static final int MAX_EVENTS = 8;

    private final LLMClient llm;
    private final String plannerPrompt;

    public PlotPlanner(LLMClient llm) {
        this.llm = llm;
        this.plannerPrompt = StoryAgent.loadPrompt("plot-planner");
    }

    /**
     * Asks the model for the hidden pivotal events, built from the scenario and the user's plot basis
     * (or from the scenario alone when the basis is empty). Every event starts as PENDING, with ids
     * E1, E2, ...
     *
     * @throws LLMException if the model declines, fails, or returns no usable events
     */
    public Plot createPlot(Scenario scenario, String basis) {
        String cleanBasis = basis == null ? "" : basis.strip();
        String message = "## Scenario\n" + scenario.describe() + "\n\n## Plot basis from the user\n"
                + (cleanBasis.isEmpty() ? "None. Invent the plot from the scenario." : cleanBasis);
        LLMResponse reply = llm.generate(new LLMRequest(plannerPrompt, List.of(LLMMessage.user(message)), List.of(),
                4_000));
        if (reply.isRefused()) {
            throw new LLMException("Claude declined to plan a story for this scenario. Try changing it.");
        }
        List<PivotalEvent> events = new ArrayList<>();
        for (String description : parseEvents(reply.getText())) {
            if (events.size() < MAX_EVENTS) {
                events.add(new PivotalEvent("E" + (events.size() + 1), description));
            }
        }
        if (events.isEmpty()) {
            throw new LLMException("Claude did not return any pivotal events. Try starting the story again.");
        }
        return new Plot(cleanBasis, events);
    }

    /**
     * Reads {"events": [...]} from the reply, ignoring any text around the JSON. An event may be a
     * string or an object with a "description". Blank events are skipped; unreadable JSON gives none.
     */
    static List<String> parseEvents(String text) {
        List<String> found = new ArrayList<>();
        JsonNode root = StoryAgent.jsonObjectIn(text);
        if (root == null) {
            return found;
        }
        for (JsonNode n : root.path("events")) {
            String d = n.isTextual() ? n.asText() : n.path("description").asText("");
            if (!d.isBlank()) {
                found.add(d.strip());
            }
        }
        return found;
    }

    /**
     * The hidden steering guidance for this turn: the next pending pivotal event and how to treat it.
     * The narration call receives it and decides whether to continue, nudge or (with UpdatePlotTool, F06)
     * revise. Empty when the story has no plan.
     */
    public String planStep(StorySession session, String input) {
        Plot plot = session.getPlot();
        if (plot == null) {
            return "";
        }
        Optional<PivotalEvent> next = plot.nextPending();
        if (next.isEmpty()) {
            return "Every pivotal event has been reached. Bring the threads together towards an ending.";
        }
        long reached = plot.getEvents().stream().filter(PivotalEvent::isReached).count();
        return "Pivotal events reached so far: " + reached + ". Next pivotal event (" + next.get().getId() + "): "
                + next.get().getDescription() + "\nIf the user's input leads away from it, nudge the story back "
                + "over the next few turns rather than all at once.";
    }
}
