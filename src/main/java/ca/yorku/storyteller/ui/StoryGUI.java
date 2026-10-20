package ca.yorku.storyteller.ui;

import ca.yorku.storyteller.model.StorySession;

/**
 * Swing front end. Displays session state and forwards user actions to the controller.
 *
 * <p>Features: F01-F12.
 * <p>Patterns: MVC (View), Observer (Concrete observer).
 * <p>TODO (Stage 1 design): showError(message).
 */
public class StoryGUI implements StoryObserver {

    @Override
    public void onStoryChanged(StorySession session) {
        // TODO: refresh the displayed lore, characters and turns
    }

    public static void main(String[] args) {
        // TODO: wire StoryController, StoryAgent and ClaudeAdapter, then start the front end
    }
}
