package ca.yorku.storyteller.persistence;

import ca.yorku.storyteller.model.StorySession;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Saves, loads and lists story sessions.
 *
 * <p>Features: F11.
 */
public interface SessionRepository {

    /**
     * Saves the whole session, replacing any earlier save with the same id.
     *
     * @throws UncheckedIOException if it cannot be written
     */
    void save(StorySession session);

    /**
     * Loads a saved session. The result has no observers attached.
     *
     * @throws IllegalArgumentException if no session with that id was saved
     * @throws UncheckedIOException     if the save exists but cannot be read or is damaged
     */
    StorySession load(String id);

    /** Ids of the saved sessions, most recently saved first. */
    List<String> listSessions();
}
