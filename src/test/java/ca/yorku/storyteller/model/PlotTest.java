package ca.yorku.storyteller.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link Plot} and {@link PivotalEvent}. */
class PlotTest {

    private Plot plot;

    @BeforeEach
    void setUp() {
        plot = new Plot("A heist that goes wrong", List.of(
                new PivotalEvent("E1", "The crew meets the fence."),
                new PivotalEvent("E2", "The vault is already empty.")));
    }

    @Test
    void plotNeedsEventsWithUniqueIds() {
        assertThrows(IllegalArgumentException.class, () -> new Plot("x", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Plot("x", List.of(
                new PivotalEvent("E1", "a"), new PivotalEvent("E1", "b"))));
        assertThrows(IllegalArgumentException.class, () -> new PivotalEvent("E1", " "));
    }

    @Test
    void markReachedAdvancesNextPending() {
        assertEquals("E1", plot.nextPending().orElseThrow().getId());
        plot.markReached("E1");
        assertTrue(plot.getEvents().get(0).isReached());
        assertEquals("E2", plot.nextPending().orElseThrow().getId());
        assertFalse(plot.isComplete());
        plot.markReached("E2");
        assertTrue(plot.nextPending().isEmpty());
        assertTrue(plot.isComplete());
    }

    @Test
    void markReachedRejectsUnknownOrAlreadyReachedEvent() {
        assertThrows(IllegalArgumentException.class, () -> plot.markReached("E9"));
        plot.markReached("E1");
        assertThrows(IllegalArgumentException.class, () -> plot.markReached("E1"));
    }

    @Test
    void replacePendingDropsPendingAndKeepsReached() {
        plot.markReached("E1");
        plot.replacePending(List.of(new PivotalEvent("E3", "The fence betrays them.")));
        assertEquals(EventStatus.REACHED, plot.getEvents().get(0).getStatus());
        assertEquals(EventStatus.DROPPED, plot.getEvents().get(1).getStatus());
        assertEquals("E3", plot.nextPending().orElseThrow().getId());
    }

    @Test
    void replacePendingRejectsEmptyListOrReusedId() {
        assertThrows(IllegalArgumentException.class, () -> plot.replacePending(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> plot.replacePending(List.of(new PivotalEvent("E1", "Reused id."))));
        assertEquals(EventStatus.PENDING, plot.getEvents().get(1).getStatus());
    }

    @Test
    void allDroppedIsNotComplete() {
        Plot p = new Plot("", List.of(new PivotalEvent("E1", "a")));
        assertFalse(p.isComplete());
    }

    @Test
    void copyIsDeep() {
        Plot before = plot.copy();
        plot.markReached("E1");
        assertEquals(EventStatus.PENDING, before.getEvents().get(0).getStatus());
        assertEquals("A heist that goes wrong", before.getBasis());
    }
}
