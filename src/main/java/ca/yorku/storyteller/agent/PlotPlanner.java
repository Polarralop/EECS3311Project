package ca.yorku.storyteller.agent;

import ca.yorku.storyteller.llm.LLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMMessage;
import ca.yorku.storyteller.llm.LLMRequest;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.model.EventStatus;
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
 * <p>TODO (Stage 1 design): readyToEnd() (F12).
 * <p>Patterns: Facade (Subsystem).
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
     * The hidden steering guidance for this turn (F06): how far the plan has come, the next pending
     * pivotal event, the later ones, and how to use {@code update_plot}. The narration call receives it
     * and itself decides whether to continue, nudge the story back or revise the plan through
     * UpdatePlotTool. Empty when the story has no plan.
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
        String id = next.get().getId();
        StringBuilder sb = new StringBuilder("Pivotal events reached so far: " + reached + ". Next pivotal event ("
                + id + "): " + next.get().getDescription());
        List<PivotalEvent> later = plot.getEvents().stream()
                .filter(e -> e.getStatus() == EventStatus.PENDING && e != next.get()).toList();
        if (!later.isEmpty()) {
            sb.append("\nLater pivotal events: ");
            sb.append(String.join("; ", later.stream().map(e -> e.getId() + ": " + e.getDescription()).toList()));
        }
        sb.append("\nIf the user's input leads away from ").append(id).append(", nudge the story back over the next ")
                .append("few turns rather than all at once. When your narration makes ").append(id)
                .append(" happen, call update_plot with action \"reached\" and event_id \"").append(id)
                .append("\". If the user's choices have made the pending events impossible or pointless, call ")
                .append("update_plot with action \"revise\" and new events that fit what has happened.");
        return sb.toString();
    }
}
