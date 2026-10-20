package ca.yorku.storyteller.ui;

import ca.yorku.storyteller.model.StorySession;

/**
 * Command-line front end with the same functionality as the GUI.
 *
 * <p>Features: F01-F12.
 * <p>Patterns: MVC (View), Observer (Concrete observer).
 * <p>TODO (Stage 1 design): showError(message).
 */
public class StoryCLI implements StoryObserver {

    @Override
    public void onStoryChanged(StorySession session) {
        // TODO: refresh the displayed lore, characters and turns
    }

    public static void main(String[] args) {
        // TODO: wire StoryController, StoryAgent and ClaudeAdapter, then start the front end
    }
}
