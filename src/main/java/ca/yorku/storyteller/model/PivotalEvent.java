package ca.yorku.storyteller.model;

/**
 * One hidden event the story is steered towards.
 *
 * <p>Features: F05 F06.
 * <p>Patterns: MVC (Model).
 */
public class PivotalEvent {

    private String id;
    private String description;
    private EventStatus status = EventStatus.PENDING;

    /** For JSON loading only. */
    private PivotalEvent() {
    }

    /**
     * @throws IllegalArgumentException if the id or description is empty
     */
    public PivotalEvent(String id, String description) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A pivotal event needs an id.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("A pivotal event needs a description.");
        }
        this.id = id.strip();
        this.description = description.strip();
    }

    PivotalEvent copy() {
        PivotalEvent e = new PivotalEvent(id, description);
        e.status = status;
        return e;
    }

    public boolean isReached() {
        return status == EventStatus.REACHED;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public EventStatus getStatus() {
        return status;
    }

    void setStatus(EventStatus status) {
        this.status = status;
    }
}
