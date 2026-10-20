package ca.yorku.storyteller.model;

/**
 * A directed relationship from one character to another. It is stored on the source character
 * and is not mirrored on the target.
 *
 * <p>Features: F03.
 * <p>Patterns: MVC (Model).
 */
public class Relationship {

    private String targetName;
    private String type;
    private String notes;

    /** For JSON loading only. */
    private Relationship() {
    }

    /**
     * @param targetName the character this relationship points at
     * @param type       a short label such as "sister" or "rival"
     * @param notes      optional free text
     * @throws IllegalArgumentException if the target or type is empty
     */
    public Relationship(String targetName, String type, String notes) {
        if (targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("A relationship needs a target character.");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("A relationship needs a type, for example \"rival\".");
        }
        this.targetName = targetName.strip();
        this.type = type.strip();
        this.notes = notes == null ? "" : notes.strip();
    }

    public String getTargetName() {
        return targetName;
    }

    /** Used when the target character is renamed. */
    void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getType() {
        return type;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public String toString() {
        return type + " of " + targetName + (notes.isEmpty() ? "" : " (" + notes + ")");
    }
}
