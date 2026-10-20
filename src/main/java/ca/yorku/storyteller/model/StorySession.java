package ca.yorku.storyteller.model;

import ca.yorku.storyteller.ui.StoryObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Root of the story state: scenario, lore, characters, plot, turns, proposals and summary. Notifies observers on change.
 *
 * <p>Features: F01-F12.
 * <p>Patterns: MVC (Model), Observer (Subject).
 *
 * <p>The session does not notify on every setter: the controller makes all the changes for one user
 * action and then calls {@link #notifyObservers()} once, so the front ends refresh once per action.
 */
public class StorySession {

    private String id;
    private Scenario scenario;
    private Plot plot;
    private LoreBook loreBook = new LoreBook();
    private List<Character> characters = new ArrayList<>();
    private List<Turn> turns = new ArrayList<>();
    private List<LoreProposal> proposals = new ArrayList<>();
    private String summary = "";
    private int summarizedUpTo;
    private int nextProposalNumber = 1;

    /** Front ends watching this session. Transient: observers are never saved (F11). */
    private transient List<StoryObserver> observers = new ArrayList<>();

    /** For JSON loading only. */
    private StorySession() {
    }

    public StorySession(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A story session needs an id.");
        }
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public LoreBook getLoreBook() {
        return loreBook;
    }

    // ---- observer (subject side) ----

    /** Registers a front end. Attaching the same observer twice has no effect. */
    public void attach(StoryObserver observer) {
        if (!observers().contains(observer)) {
            observers().add(observer);
        }
    }

    public void detach(StoryObserver observer) {
        observers().remove(observer);
    }

    /** Tells every attached front end that the session changed. */
    public void notifyObservers() {
        for (StoryObserver o : List.copyOf(observers())) {
            o.onStoryChanged(this);
        }
    }

    /** A session read from JSON has no observer list yet, because the field is transient. */
    private List<StoryObserver> observers() {
        if (observers == null) {
            observers = new ArrayList<>();
        }
        return observers;
    }

    // ---- scenario and plot (F01, F05) ----

    public Scenario getScenario() {
        return scenario;
    }

    public void setScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    /** The hidden plan. Front ends must not display it. */
    public Plot getPlot() {
        return plot;
    }

    public void setPlot(Plot plot) {
        this.plot = plot;
    }

    // ---- turns (F04, F10) ----

    public void addTurn(Turn turn) {
        turns.add(turn);
    }

    /**
     * Removes and returns the latest turn (undo, F10).
     *
     * @throws IllegalStateException if there are no turns
     */
    public Turn removeLastTurn() {
        if (turns.isEmpty()) {
            throw new IllegalStateException("There is no turn to remove.");
        }
        Turn removed = turns.remove(turns.size() - 1);
        if (summarizedUpTo > turns.size()) {
            summarizedUpTo = turns.size();
        }
        return removed;
    }

    /** The latest turn, or empty before the first turn. */
    public Optional<Turn> lastTurn() {
        return turns.isEmpty() ? Optional.empty() : Optional.of(turns.get(turns.size() - 1));
    }

    public List<Turn> getTurns() {
        return List.copyOf(turns);
    }

    /** The number the next turn will get, starting at 1. */
    public int nextTurnNumber() {
        return turns.size() + 1;
    }

    // ---- lore proposals (F07) ----

    /** Stores a pending proposal, giving it the next id ("P1", "P2", ...) if it has none. */
    public void addProposal(LoreProposal proposal) {
        if (proposal.getId() == null || proposal.getId().isBlank()) {
            proposal.setId("P" + nextProposalNumber++);
        }
        proposals.add(proposal);
    }

    /**
     * Approves a pending proposal and adds its entry to the lore book. If the lore book refuses the
     * entry (for example a duplicate title), the proposal stays pending.
     *
     * @throws IllegalArgumentException if there is no pending proposal with that id, or the lore book refuses it
     */
    public LoreEntry approveProposal(String proposalId) {
        LoreProposal proposal = findPendingProposal(proposalId);
        LoreEntry entry = proposal.approve();
        try {
            loreBook.add(entry);
        } catch (IllegalArgumentException e) {
            proposal.reopen();
            throw e;
        }
        return entry;
    }

    /**
     * @throws IllegalArgumentException if there is no pending proposal with that id
     */
    public void rejectProposal(String proposalId) {
        findPendingProposal(proposalId).reject();
    }

    public List<LoreProposal> getPendingProposals() {
        return proposals.stream().filter(p -> p.getStatus() == ProposalStatus.PENDING).toList();
    }

    public List<LoreProposal> getProposals() {
        return List.copyOf(proposals);
    }

    private LoreProposal findPendingProposal(String proposalId) {
        return proposals.stream()
                .filter(p -> p.getId().equalsIgnoreCase(proposalId) && p.getStatus() == ProposalStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("There is no pending proposal " + proposalId + "."));
    }

    // ---- memory summary (F09) ----

    public String getSummary() {
        return summary;
    }

    /** Number of turns already folded into the summary. */
    public int getSummarizedUpTo() {
        return summarizedUpTo;
    }

    /**
     * @throws IllegalArgumentException if upTo is negative or beyond the turns that exist
     */
    public void setSummary(String summary, int upTo) {
        if (upTo < 0 || upTo > turns.size()) {
            throw new IllegalArgumentException("Cannot summarise up to turn " + upTo + ".");
        }
        this.summary = summary == null ? "" : summary;
        this.summarizedUpTo = upTo;
    }

    // ---- characters (F03) ----

    /**
     * Adds a new character, then checks each of its relationships.
     *
     * @throws IllegalArgumentException if the name is taken or a relationship target is missing or itself
     */
    public void addCharacter(Character character) {
        if (findCharacter(character.getName()).isPresent()) {
            throw new IllegalArgumentException("A character named " + character.getName() + " already exists.");
        }
        requireValidRelationships(character);
        characters.add(character);
    }

    /**
     * Replaces the character currently called {@code originalName}. If the name changed, relationships
     * that other characters have with it follow the new name.
     *
     * @throws IllegalArgumentException if the original is missing, the new name is taken, or a relationship is invalid
     */
    public void updateCharacter(String originalName, Character updated) {
        Character existing = findCharacter(originalName)
                .orElseThrow(() -> new IllegalArgumentException("There is no character named " + originalName + "."));
        Optional<Character> sameName = findCharacter(updated.getName());
        if (sameName.isPresent() && sameName.get() != existing) {
            throw new IllegalArgumentException("A character named " + updated.getName() + " already exists.");
        }
        int index = characters.indexOf(existing);
        characters.set(index, updated);
        try {
            requireValidRelationships(updated);
        } catch (IllegalArgumentException e) {
            characters.set(index, existing);
            throw e;
        }
        if (!existing.getName().equalsIgnoreCase(updated.getName())) {
            for (Character c : characters) {
                for (Relationship r : c.getRelationships()) {
                    if (r.getTargetName().equalsIgnoreCase(existing.getName())) {
                        r.setTargetName(updated.getName());
                    }
                }
            }
        }
    }

    /**
     * Removes the character and every relationship that points at it.
     *
     * @throws IllegalArgumentException if there is no such character
     */
    public void removeCharacter(String name) {
        Character existing = findCharacter(name)
                .orElseThrow(() -> new IllegalArgumentException("There is no character named " + name + "."));
        characters.remove(existing);
        for (Character c : characters) {
            c.removeRelationship(existing.getName());
        }
    }

    /**
     * Adds a relationship from one stored character to another.
     *
     * @throws IllegalArgumentException if either character is missing or they are the same character
     */
    public void addRelationship(String fromName, Relationship relationship) {
        Character from = findCharacter(fromName)
                .orElseThrow(() -> new IllegalArgumentException("There is no character named " + fromName + "."));
        requireTarget(from, relationship);
        from.addRelationship(relationship);
    }

    /** Finds a character by name, ignoring case. */
    public Optional<Character> findCharacter(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return characters.stream().filter(c -> c.getName().equalsIgnoreCase(name.strip())).findFirst();
    }

    public List<Character> getCharacters() {
        return List.copyOf(characters);
    }

    private void requireValidRelationships(Character character) {
        for (Relationship r : character.getRelationships()) {
            requireTarget(character, r);
        }
    }

    private void requireTarget(Character from, Relationship r) {
        if (r.getTargetName().equalsIgnoreCase(from.getName())) {
            throw new IllegalArgumentException(from.getName() + " cannot have a relationship with themself.");
        }
        if (findCharacter(r.getTargetName()).isEmpty()) {
            throw new IllegalArgumentException("There is no character named " + r.getTargetName() + " to relate to.");
        }
    }
}
