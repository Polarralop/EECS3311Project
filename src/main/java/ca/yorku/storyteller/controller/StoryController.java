package ca.yorku.storyteller.controller;

import ca.yorku.storyteller.model.Character;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.Relationship;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.persistence.SessionRepository;
import ca.yorku.storyteller.ui.StoryObserver;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Single entry point for both front ends. Validates input, creates commands and delegates story work to the agent.
 *
 * <p>Every method that changes the story notifies the session's observers once. A method that cannot
 * complete throws {@link IllegalArgumentException} or {@link IllegalStateException} (or
 * {@link java.io.UncheckedIOException} for save files) with a message the front end shows as an error;
 * nothing is changed in that case.
 *
 * <p>Features: F01-F12.
 * <p>Patterns: MVC (Controller), Command (Client), Facade (Client).
 * <p>TODO (Stage 1 design): generateScenario(prompt), startStory(scenario, plotBasis), submitTurn(), undoTurn(),
 * retryTurn(), editTurn(), resolveProposal(), requestEnding().
 */
public class StoryController {

    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SessionRepository repository;
    private final CommandHistory history = new CommandHistory();
    private final List<StoryObserver> observers = new ArrayList<>();
    private StorySession session;

    public StoryController(SessionRepository repository) {
        this.repository = repository;
    }

    /** Registers a front end; it is attached to the current session and to every later one. */
    public void addObserver(StoryObserver observer) {
        observers.add(observer);
        if (session != null) {
            session.attach(observer);
        }
    }

    /** The current story, or null before one is created or loaded. */
    public StorySession getSession() {
        return session;
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    /** Starts an empty story (lore and characters can be prepared before the scenario). */
    public StorySession newSession() {
        String id = "story-" + LocalDateTime.now().format(ID_FORMAT);
        while (repository.listSessions().contains(id)) {
            id = id + "x";
        }
        switchTo(new StorySession(id));
        return session;
    }

    // ---- lore (F02) ----

    /**
     * Adds the entry if it is new (no id, or an id not in the lore book), otherwise replaces the stored one.
     *
     * @throws IllegalArgumentException if the entry is invalid or its title is taken
     */
    public void saveLoreEntry(LoreEntry entry) {
        StorySession s = requireSession();
        if (entry.getId() != null && s.getLoreBook().find(entry.getId()).isPresent()) {
            s.getLoreBook().update(entry);
        } else {
            s.getLoreBook().add(entry);
        }
        s.notifyObservers();
    }

    public void removeLoreEntry(String id) {
        StorySession s = requireSession();
        s.getLoreBook().remove(id);
        s.notifyObservers();
    }

    /** Matching entries, pinned first. A blank query lists everything. */
    public List<LoreEntry> searchLore(String query) {
        return requireSession().getLoreBook().search(query);
    }

    /** A copy of the entry for editing, so the stored one changes only through {@link #saveLoreEntry}. */
    public LoreEntry getLoreEntry(String id) {
        return requireSession().getLoreBook().find(id)
                .orElseThrow(() -> new IllegalArgumentException("There is no lore entry with id " + id + "."))
                .copy();
    }

    // ---- characters (F03) ----

    /**
     * Adds a new character when {@code originalName} is null, otherwise replaces the character with that name.
     *
     * @throws IllegalArgumentException if the name is taken or a relationship target is missing
     */
    public void saveCharacter(String originalName, Character character) {
        StorySession s = requireSession();
        if (originalName == null) {
            s.addCharacter(character);
        } else {
            s.updateCharacter(originalName, character);
        }
        s.notifyObservers();
    }

    public void removeCharacter(String name) {
        StorySession s = requireSession();
        s.removeCharacter(name);
        s.notifyObservers();
    }

    public List<Character> listCharacters() {
        return requireSession().getCharacters();
    }

    /** A copy of the character for editing. */
    public Character getCharacter(String name) {
        return requireSession().findCharacter(name)
                .orElseThrow(() -> new IllegalArgumentException("There is no character named " + name + "."))
                .copy();
    }

    public void addRelationship(String fromName, String targetName, String type, String notes) {
        StorySession s = requireSession();
        s.addRelationship(fromName, new Relationship(targetName, type, notes));
        s.notifyObservers();
    }

    public void removeRelationship(String fromName, String targetName) {
        StorySession s = requireSession();
        Character from = s.findCharacter(fromName)
                .orElseThrow(() -> new IllegalArgumentException("There is no character named " + fromName + "."));
        if (!from.removeRelationship(targetName)) {
            throw new IllegalArgumentException(from.getName() + " has no relationship with " + targetName + ".");
        }
        s.notifyObservers();
    }

    // ---- save and load (F11) ----

    public void saveSession() {
        repository.save(requireSession());
    }

    /**
     * Loads a saved story and makes it current. The undo history starts empty.
     *
     * @return a short recap to show the user
     * @throws IllegalArgumentException if there is no such save; the current story stays open
     */
    public String loadSession(String id) {
        StorySession loaded = repository.load(id);
        switchTo(loaded);
        // TODO (F09): replace with StoryAgent.recap(session) once the agent is wired in.
        return describe(loaded);
    }

    public List<String> listSessions() {
        return repository.listSessions();
    }

    private void switchTo(StorySession next) {
        if (session != null) {
            for (StoryObserver o : observers) {
                session.detach(o);
            }
        }
        session = next;
        history.clear();
        for (StoryObserver o : observers) {
            session.attach(o);
        }
        session.notifyObservers();
    }

    private static String describe(StorySession s) {
        String title = s.getScenario() == null || s.getScenario().getTitle().isEmpty()
                ? s.getId() : s.getScenario().getTitle();
        return "Loaded \"" + title + "\": " + s.getLoreBook().size() + " lore entries, "
                + s.getCharacters().size() + " characters, " + s.getTurns().size() + " turns.";
    }

    private StorySession requireSession() {
        if (session == null) {
            throw new IllegalStateException("Start a new story or load one first.");
        }
        return session;
    }
}
