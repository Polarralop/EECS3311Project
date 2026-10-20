package ca.yorku.storyteller.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Root of the story state: scenario, lore, characters, plot, turns, proposals and summary. Notifies observers on change.
 *
 * <p>Features: F01-F12.
 * <p>Patterns: MVC (Model), Observer (Subject).
 * <p>TODO (Stage 1 design): attach(observer), notifyObservers(), addTurn(), addProposal(), approveProposal().
 */
public class StorySession {

    private String id;
    private LoreBook loreBook = new LoreBook();
    private List<Character> characters = new ArrayList<>();

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
