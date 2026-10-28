package ca.yorku.storyteller.controller;

import ca.yorku.storyteller.agent.InputModeStrategy;
import ca.yorku.storyteller.agent.StoryAgent;
import ca.yorku.storyteller.model.Plot;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;

/**
 * Runs one story turn and remembers the turn it added and the pivotal events as they were before.
 *
 * <p>Features: F04 F10.
 * <p>Patterns: Command (Concrete command).
 */
public class SubmitTurnCommand implements StoryCommand {

    private final StorySession session;
    private final StoryAgent agent;
    private final String input;
    private final InputModeStrategy mode;
    private Plot plotBefore;
    private Turn result;

    public SubmitTurnCommand(StorySession session, StoryAgent agent, String input, InputModeStrategy mode) {
        this.session = session;
        this.agent = agent;
        this.input = input;
        this.mode = mode;
    }

    /**
     * Copies the plot, has the agent narrate the turn and adds it to the session. If the agent fails,
     * the plot is put back (tool calls may have changed it mid-turn) and no turn is added.
     */
    @Override
    public void execute() {
        plotBefore = session.getPlot() == null ? null : session.getPlot().copy();
        try {
            result = agent.takeTurn(session, input, mode);
        } catch (RuntimeException e) {
            session.setPlot(plotBefore);
            throw e;
        }
        session.addTurn(result);
    }

    /** Removes the turn this command added and restores the pivotal events from before it. */
    @Override
    public void undo() {
        session.removeLastTurn();
        session.setPlot(plotBefore);
    }

    /** The user's original input, so retry can send it again. */
    public String getInput() {
        return input;
    }

    public InputModeStrategy getMode() {
        return mode;
    }

    /** The turn added by {@link #execute()}, or null before it ran. */
    public Turn getResult() {
        return result;
    }
}
