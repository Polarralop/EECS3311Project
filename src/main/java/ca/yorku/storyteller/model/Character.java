package ca.yorku.storyteller.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A character and its relationships to other characters.
 *
 * <p>Features: F03.
 * <p>Patterns: MVC (Model).
 */
public class Character {

    private String name;
    private String description;
    private List<String> traits = new ArrayList<>();
    private List<Relationship> relationships = new ArrayList<>();

    /** For JSON loading only. */
    private Character() {
    }

    /**
     * @throws IllegalArgumentException if the name is empty
     */
    public Character(String name, String description, List<String> traits) {
        setName(name);
        setDescription(description);
        setTraits(traits);
    }

    /** Returns a deep copy, so a front end can edit it without touching the stored character. */
    public Character copy() {
        Character c = new Character(name, description, traits);
        for (Relationship r : relationships) {
            c.relationships.add(new Relationship(r.getTargetName(), r.getType(), r.getNotes()));
        }
        return c;
    }

    /**
     * Adds a relationship, replacing any existing one to the same target.
     *
     * @throws IllegalArgumentException if the relationship points at this character
     */
    public void addRelationship(Relationship r) {
        if (r.getTargetName().equalsIgnoreCase(name)) {
            throw new IllegalArgumentException(name + " cannot have a relationship with themself.");
        }
        removeRelationship(r.getTargetName());
        relationships.add(r);
    }

    /** Removes the relationship to the named target. Returns false if there was none. */
    public boolean removeRelationship(String targetName) {
        return relationships.removeIf(r -> r.getTargetName().equalsIgnoreCase(targetName));
    }

    public Optional<Relationship> relationshipTo(String targetName) {
        return relationships.stream().filter(r -> r.getTargetName().equalsIgnoreCase(targetName)).findFirst();
    }

    /** The profile text the agent receives when it looks the character up during a turn (F04). */
    public String describe() {
        StringBuilder sb = new StringBuilder(name);
        if (!description.isEmpty()) {
            sb.append(": ").append(description);
        }
        if (!traits.isEmpty()) {
            sb.append("\nTraits: ").append(String.join(", ", traits));
        }
        for (Relationship r : relationships) {
            sb.append("\nRelationship: ").append(r);
        }
        return sb.toString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A character needs a name.");
        }
        this.name = name.strip();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description.strip();
    }

    public List<String> getTraits() {
        return List.copyOf(traits);
    }

    /** Replaces the traits; blank ones are dropped. */
    public void setTraits(List<String> newTraits) {
        traits = new ArrayList<>();
        if (newTraits != null) {
            for (String t : newTraits) {
                if (t != null && !t.isBlank()) {
                    traits.add(t.strip());
                }
            }
        }
    }

    public List<Relationship> getRelationships() {
        return List.copyOf(relationships);
    }

    @Override
    public String toString() {
        return name;
    }
}
