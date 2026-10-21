package ca.yorku.storyteller.controller;

/**
 * An undoable story action.
 *
 * <p>Features: F04 F10.
 * <p>Patterns: Command (Command).
 */
public interface StoryCommand {

    /** Performs the action against the current session. */
    void execute();

    /** Reverses exactly what {@link #execute()} did. */
    void undo();
}
