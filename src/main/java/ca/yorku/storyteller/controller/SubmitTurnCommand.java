package ca.yorku.storyteller.controller;

/**
 * Runs one story turn and remembers the turn it added and the pivotal events as they were before.
 *
 * <p>Features: F04 F10.
 * <p>Patterns: Command (Concrete command).
 */
public class SubmitTurnCommand implements StoryCommand {

    // TODO (F10, week 2): implement; nothing creates this command yet.
    @Override
    public void execute() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void undo() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
