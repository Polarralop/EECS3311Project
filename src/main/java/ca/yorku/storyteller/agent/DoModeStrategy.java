package ca.yorku.storyteller.agent;

/**
 * Frames input as an action the player character takes.
 *
 * <p>Features: F04.
 * <p>Patterns: Strategy (Concrete strategy).
 */
public class DoModeStrategy implements InputModeStrategy {

    @Override
    public String getName() {
        return "Do";
    }

    @Override
    public String frame(String input) {
        return "The user's character attempts this action: " + input.strip()
                + "\nNarrate how the attempt plays out and how the world responds. Whether it succeeds is yours "
                + "to decide, but do not add further actions or words for the user's character.";
    }
}
