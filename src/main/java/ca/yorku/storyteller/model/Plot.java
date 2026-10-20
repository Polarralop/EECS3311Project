package ca.yorku.storyteller.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The hidden list of pivotal events for a story. Front ends never display it.
 *
 * <p>Features: F05 F06 F10 F12.
 * <p>Patterns: MVC (Model).
 */
public class Plot {

    private String basis;
    private List<PivotalEvent> events = new ArrayList<>();

    /** For JSON loading only. */
    private Plot() {
    }

    /**
     * @param basis  the user's plot idea, or empty when the agent invented the plot
     * @param events the pivotal events in story order
     * @throws IllegalArgumentException if there are no events or two share an id
     */
    public Plot(String basis, List<PivotalEvent> events) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("A plot needs at least one pivotal event.");
        }
        this.basis = basis == null ? "" : basis.strip();
        this.events = new ArrayList<>(events);
        requireUniqueIds(this.events);
    }

    /** The first event still pending, if any. */
    public Optional<PivotalEvent> nextPending() {
        return events.stream().filter(e -> e.getStatus() == EventStatus.PENDING).findFirst();
    }

    /**
     * Marks a pending event as reached.
     *
     * @throws IllegalArgumentException if there is no pending event with that id
     */
    public void markReached(String eventId) {
        PivotalEvent event = events.stream()
                .filter(e -> e.getId().equals(eventId) && e.getStatus() == EventStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("There is no pending pivotal event " + eventId + "."));
        event.setStatus(EventStatus.REACHED);
    }

    /**
     * Plot steering (F06): every pending event is dropped and the new events are appended.
     * Reached events stay as they are.
     *
     * @throws IllegalArgumentException if the list is empty or an id is already used
     */
    public void replacePending(List<PivotalEvent> newEvents) {
        if (newEvents == null || newEvents.isEmpty()) {
            throw new IllegalArgumentException("Replacing the plan needs at least one new pivotal event.");
        }
        List<PivotalEvent> all = new ArrayList<>(events);
        all.addAll(newEvents);
        requireUniqueIds(all);
        for (PivotalEvent e : events) {
            if (e.getStatus() == EventStatus.PENDING) {
                e.setStatus(EventStatus.DROPPED);
            }
        }
        events.addAll(newEvents);
    }

    /** True once at least one event was reached and none is pending. */
    public boolean isComplete() {
        return nextPending().isEmpty() && events.stream().anyMatch(PivotalEvent::isReached);
    }

    /** A deep copy, taken before a turn so undo (F10) can restore it. */
    public Plot copy() {
        Plot p = new Plot();
        p.basis = basis;
        for (PivotalEvent e : events) {
            p.events.add(e.copy());
        }
        return p;
    }

    public String getBasis() {
        return basis;
    }

    public List<PivotalEvent> getEvents() {
        return List.copyOf(events);
    }

    private static void requireUniqueIds(List<PivotalEvent> list) {
        Set<String> seen = new HashSet<>();
        for (PivotalEvent e : list) {
            if (!seen.add(e.getId())) {
                throw new IllegalArgumentException("Two pivotal events share the id " + e.getId() + ".");
            }
        }
    }
}
