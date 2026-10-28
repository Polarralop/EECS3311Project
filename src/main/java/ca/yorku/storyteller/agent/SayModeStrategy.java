package ca.yorku.storyteller.agent;

/**
 * Frames input as something the player character says.
 *
 * <p>Features: F04.
 * <p>Patterns: Strategy (Concrete strategy).
 */
public class SayModeStrategy implements InputModeStrategy {

    @Override
    public String getName() {
        return "Say";
    }

    @Override
    public String frame(String input) {
        String line = input.strip();
        if (!line.startsWith("\"")) {
            line = "\"" + line + "\"";
        }
        return "The user's character says, aloud: " + line
                + "\nOpen with the line exactly as written, then narrate how the listeners react and answer.";
    }
}
