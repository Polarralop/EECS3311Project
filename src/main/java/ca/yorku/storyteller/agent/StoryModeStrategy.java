package ca.yorku.storyteller.agent;

/**
 * Frames input as narration the user writes directly.
 *
 * <p>Features: F04.
 * <p>Patterns: Strategy (Concrete strategy).
 */
public class StoryModeStrategy implements InputModeStrategy {

    @Override
    public String getName() {
        return "Story";
    }

    @Override
    public String frame(String input) {
        return "The user wrote this passage of the story themselves. Treat it as having happened, and continue "
                + "directly from where it ends without repeating it:\n" + input.strip();
    }
}
