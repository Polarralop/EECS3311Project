package ca.yorku.storyteller.controller;

import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;

/**
 * Replaces the text of the latest turn and can restore the original.
 *
 * <p>Features: F10.
 * <p>Patterns: Command (Concrete command).
 */
public class EditTurnCommand implements StoryCommand {

    private final StorySession session;
    private final String newNarration;
    private Turn turn;
    private String oldNarration;

    public EditTurnCommand(StorySession session, String newNarration) {
        this.session = session;
        this.newNarration = newNarration;
    }

    /**
     * Remembers the latest turn's narration and replaces it.
     *
     * @throws IllegalStateException if the story has no turns
     * @throws IllegalArgumentException if the new text is empty; nothing changes
     */
    @Override
    public void execute() {
        turn = session.lastTurn().orElseThrow(() -> new IllegalStateException("There is no turn to edit."));
        oldNarration = turn.getNarration();
        turn.setNarration(newNarration);
    }

    /** Puts the original narration back. */
    @Override
    public void undo() {
        turn.setNarration(oldNarration);
    }

    public String getOldNarration() {
        return oldNarration;
    }
}
