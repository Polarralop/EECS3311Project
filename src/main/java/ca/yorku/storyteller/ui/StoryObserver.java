package ca.yorku.storyteller.ui;

import ca.yorku.storyteller.model.StorySession;

/**
 * Receives a notification whenever the story session changes, so a front end can refresh.
 *
 * <p>Features: F02 F03 F04 F07 F08 F10 F11.
 * <p>Patterns: Observer (Observer).
 */
public interface StoryObserver {

    /** Called after the session has changed; the observer re-reads whatever it displays. */
    void onStoryChanged(StorySession session);
}
