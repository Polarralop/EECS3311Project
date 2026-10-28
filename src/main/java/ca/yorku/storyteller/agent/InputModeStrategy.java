package ca.yorku.storyteller.agent;

/**
 * Frames the user's input for the model according to the chosen input mode. The rest of a turn is
 * the same whichever mode is used.
 *
 * <p>Features: F04 F10.
 * <p>Patterns: Strategy (Strategy).
 */
public interface InputModeStrategy {

    /** The mode's name as the user picks it and as it is stored on the turn: "Do", "Say" or "Story". */
    String getName();

    /** The instruction the model receives for this turn's input. */
    String frame(String input);
}
