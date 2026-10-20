package ca.yorku.storyteller.controller;

/**
 * Replaces the text of the latest turn and can restore the original.
 *
 * <p>Features: F10.
 * <p>Patterns: Command (Concrete command).
 */
public class EditTurnCommand implements StoryCommand {

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
