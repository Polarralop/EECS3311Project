package ca.yorku.storyteller.controller;

import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import java.util.List;

/**
 * Replaces the text of the latest turn and can restore the original. The turn's consistency warnings
 * (F08) were about the agent's text, so they are cleared with it and restored on undo.
 *
 * <p>Features: F10.
 * <p>Patterns: Command (Concrete command).
 */
public class EditTurnCommand implements StoryCommand {

    private final StorySession session;
    private final String newNarration;
    private Turn turn;
    private String oldNarration;
    private List<String> oldWarnings = List.of();

    public EditTurnCommand(StorySession session, String newNarration) {
        this.session = session;
        this.newNarration = newNarration;
    }

    /**
     * Remembers the latest turn's narration and warnings, replaces the narration and clears the warnings.
     *
     * @throws IllegalStateException if the story has no turns
     * @throws IllegalArgumentException if the new text is empty; nothing changes
     */
    @Override
    public void execute() {
        turn = session.lastTurn().orElseThrow(() -> new IllegalStateException("There is no turn to edit."));
        oldNarration = turn.getNarration();
        oldWarnings = turn.getWarnings();
        turn.setNarration(newNarration);
        turn.setWarnings(List.of());
    }

    /** Puts the original narration and its warnings back. */
    @Override
    public void undo() {
        turn.setNarration(oldNarration);
        turn.setWarnings(oldWarnings);
    }

    public String getOldNarration() {
        return oldNarration;
    }
}
