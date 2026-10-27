package ca.yorku.storyteller.agent;

import ca.yorku.storyteller.model.PivotalEvent;
import ca.yorku.storyteller.model.Plot;
import ca.yorku.storyteller.model.StorySession;
import java.util.Optional;

/**
 * Creates the pivotal events and decides each turn whether to steer back or revise the plan.
 *
 * <p>Features: F05 F06 F12.
 * <p>Patterns: Facade (Subsystem).
 * <p>TODO (Stage 1 design): createPlot() (F05, this week), readyToEnd() (F12).
 */
public class PlotPlanner {

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
