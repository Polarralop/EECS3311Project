package ca.yorku.storyteller.controller;

import ca.yorku.storyteller.agent.DoModeStrategy;
import ca.yorku.storyteller.agent.InputModeStrategy;
import ca.yorku.storyteller.agent.SayModeStrategy;
import ca.yorku.storyteller.agent.StoryAgent;
import ca.yorku.storyteller.agent.StoryModeStrategy;
import ca.yorku.storyteller.model.Character;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.LoreProposal;
import ca.yorku.storyteller.model.Plot;
import ca.yorku.storyteller.model.Relationship;
import ca.yorku.storyteller.model.Scenario;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import ca.yorku.storyteller.persistence.SessionRepository;
import ca.yorku.storyteller.ui.StoryObserver;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single entry point for both front ends. Validates input, creates commands and delegates story work to the agent.
 *
 * <p>Every method that changes the story notifies the session's observers once. A method that cannot
 * complete throws {@link IllegalArgumentException} or {@link IllegalStateException} (or
 * {@link java.io.UncheckedIOException} for save files, {@link ca.yorku.storyteller.llm.LLMException} for a
 * failed model call) with a message the front end shows as an error; nothing is changed in that case.
 *
 * <p>Features: F01-F12.
 * <p>Patterns: MVC (Controller), Command (Client), Facade (Client).
 * <p>TODO (Stage 1 design): requestEnding() (F12).
 */
public class StoryController {

    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SessionRepository repository;
    private final StoryAgent agent;
    private final CommandHistory history = new CommandHistory();
    private final List<StoryObserver> observers = new ArrayList<>();
    /** The input modes the user can pick, by lower-case name (Strategy: the controller selects one). */
    private final Map<String, InputModeStrategy> modes = new LinkedHashMap<>();
    private StorySession session;

    public StoryController(SessionRepository repository, StoryAgent agent) {
        this.repository = repository;
        this.agent = agent;
        for (InputModeStrategy m : List.of(new DoModeStrategy(), new SayModeStrategy(), new StoryModeStrategy())) {
            modes.put(m.getName().toLowerCase(Locale.ROOT), m);
        }
    }

    /** The mode names a front end offers: Do, Say, Story. */
    public List<String> modeNames() {
        return modes.values().stream().map(InputModeStrategy::getName).toList();
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
        switchTo(new StorySession(freshId()));
        return session;
    }

    private String freshId() {
        String id = "story-" + LocalDateTime.now().format(ID_FORMAT);
        while (repository.listSessions().contains(id) || (session != null && session.getId().equals(id))) {
            id = id + "x";
        }
        return id;
    }

    // ---- scenario and pivotal events (F01, F05) ----

    /**
     * Has the agent draft a scenario from a short idea. Nothing changes until the user edits the draft
     * and calls {@link #startStory}.
     *
     * @throws IllegalArgumentException if the idea is empty
     * @throws ca.yorku.storyteller.llm.LLMException if Claude declines or the call fails
     */
    public Scenario generateScenario(String prompt) {
        return agent.generateScenario(prompt);
    }

    /**
     * Starts the story: checks the scenario, has the agent plan the hidden pivotal events, then opens
     * the story with both. Lore and characters prepared in an empty, unstarted session are kept;
     * otherwise a new session is created. The plan is never returned to the front end.
     *
     * @throws IllegalArgumentException if the premise or setting is empty
     * @throws ca.yorku.storyteller.llm.LLMException if planning fails; no story is started
     */
    public void startStory(Scenario scenario, String plotBasis) {
        Scenario ready = scenario.readyToStart();
        Plot plot = agent.planPlot(ready, plotBasis);
        StorySession target = session;
        if (target == null || target.getScenario() != null || !target.getTurns().isEmpty()) {
            target = new StorySession(freshId());
        }
        target.setScenario(ready);
        target.setPlot(plot);
        if (target == session) {
            history.clear();
            session.notifyObservers();
        } else {
            switchTo(target);
        }
    }

    /** True once the current story has a scenario and can take turns. */
    public boolean isStarted() {
        return session != null && session.getScenario() != null;
    }

    // ---- story turns (F04, F10) ----

    /**
     * Runs one turn as an undoable command: the agent narrates the input in the chosen mode and the
     * turn is added to the story.
     *
     * @throws IllegalArgumentException if the input is empty or the mode is unknown
     * @throws IllegalStateException if no story has been started
     * @throws ca.yorku.storyteller.llm.LLMException if Claude declines or the call fails; no turn is added
     */
    public Turn submitTurn(String input, String modeName) {
        StorySession s = requireStarted();
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Type something for the turn first.");
        }
        SubmitTurnCommand command = new SubmitTurnCommand(s, agent, input, mode(modeName));
        int proposalsBefore = s.getProposals().size();
        try {
            history.run(command);
        } catch (RuntimeException e) {
            // Proposals raised before the failure stay pending (as for an undone turn), so show them.
            if (s.getProposals().size() != proposalsBefore) {
                s.notifyObservers();
            }
            throw e;
        }
        s.notifyObservers();
        return command.getResult();
    }

    /**
     * Undoes the most recent action: a turn is removed and the pivotal events go back to how they were
     * before it; an edit gets its original narration back.
     *
     * @throws IllegalStateException if there is nothing to undo (for example right after a load)
     */
    public void undoTurn() {
        StorySession s = requireSession();
        if (!history.canUndo()) {
            throw new IllegalStateException("There is nothing to undo.");
        }
        history.undoLast();
        s.notifyObservers();
    }

    /**
     * Regenerates the most recent turn from the same input and mode. Any edits of that turn are undone
     * with it. If the new attempt fails, the turn stays undone and the error is passed on, so the front
     * end can give the user their input back.
     *
     * @throws IllegalStateException if there is no turn to retry
     */
    public Turn retryTurn() {
        StorySession s = requireStarted();
        SubmitTurnCommand last = history.latest(SubmitTurnCommand.class)
                .orElseThrow(() -> new IllegalStateException("There is no turn to retry."));
        while (history.peekLast().orElseThrow() != last) {
            history.undoLast();
        }
        history.undoLast();
        SubmitTurnCommand again = new SubmitTurnCommand(s, agent, last.getInput(), last.getMode());
        try {
            history.run(again);
        } finally {
            s.notifyObservers();
        }
        return again.getResult();
    }

    /**
     * Replaces the narration of the most recent turn with the user's text, as an undoable command.
     *
     * @throws IllegalArgumentException if the text is empty; the narration is unchanged
     * @throws IllegalStateException if the story has no turns
     */
    public void editTurn(String newNarration) {
        StorySession s = requireStarted();
        if (newNarration == null || newNarration.isBlank()) {
            throw new IllegalArgumentException("The narration cannot be empty. Undo the turn instead if you want it gone.");
        }
        history.run(new EditTurnCommand(s, newNarration.strip()));
        s.notifyObservers();
    }

    private InputModeStrategy mode(String name) {
        InputModeStrategy m = name == null ? null : modes.get(name.strip().toLowerCase(Locale.ROOT));
        if (m == null) {
            throw new IllegalArgumentException("Unknown mode \"" + name + "\". Use " + String.join(", ", modeNames()) + ".");
        }
        return m;
    }

    // ---- lore proposals (F07) ----

    /** The agent's proposals still waiting for the user, oldest first. */
    public List<LoreProposal> listPendingProposals() {
        return requireSession().getPendingProposals();
    }

    /**
     * Applies the user's decision on a proposal: approved, it becomes a new lore entry; rejected, it is
     * marked rejected and nothing is added. This is the only way agent-suggested lore reaches the lore book.
     *
     * @throws IllegalArgumentException if no pending proposal has that id, or the lore book refuses the
     *                                  entry (for example its title was taken meanwhile); it stays pending
     */
    public void resolveProposal(String id, boolean approved) {
        StorySession s = requireSession();
        if (approved) {
            s.approveProposal(id);
        } else {
            s.rejectProposal(id);
        }
        s.notifyObservers();
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

    private StorySession requireStarted() {
        StorySession s = requireSession();
        if (s.getScenario() == null) {
            throw new IllegalStateException("Start the story first.");
        }
        return s;
    }

    private StorySession requireSession() {
        if (session == null) {
            throw new IllegalStateException("Start a new story or load one first.");
        }
        return session;
    }
}
