package ca.yorku.storyteller.model;

/**
 * Progress state of a pivotal event.
 *
 * <p>Features: F05 F06.
 */
public enum EventStatus {
    /** Not reached yet; the agent steers towards it. */
    PENDING,
    /** The story has reached it. */
    REACHED,
    /** Replaced by plot steering (F06) and no longer pursued. */
    DROPPED
}
