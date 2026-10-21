package ca.yorku.storyteller.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link CommandHistory}. */
class CommandHistoryTest {

    /** Records execute and undo calls in a shared log. */
    private record Logged(String name, List<String> log) implements StoryCommand {
        @Override
        public void execute() {
            log.add("do " + name);
        }

        @Override
        public void undo() {
            log.add("undo " + name);
        }
    }

    @Test
    void undoesInReverseOrder() {
        List<String> log = new ArrayList<>();
        CommandHistory h = new CommandHistory();
        StoryCommand a = new Logged("a", log);
        StoryCommand b = new Logged("b", log);
        h.run(a);
        h.run(b);
        assertSame(b, h.undoLast());
        assertSame(a, h.undoLast());
        assertEquals(List.of("do a", "do b", "undo b", "undo a"), log);
        assertFalse(h.canUndo());
    }

    @Test
    void undoWithNothingDoneFails() {
        assertThrows(IllegalStateException.class, () -> new CommandHistory().undoLast());
    }

    @Test
    void failedCommandIsNotRemembered() {
        CommandHistory h = new CommandHistory();
        StoryCommand failing = new StoryCommand() {
            @Override
            public void execute() {
                throw new IllegalStateException("model unavailable");
            }

            @Override
            public void undo() {
            }
        };
        assertThrows(IllegalStateException.class, () -> h.run(failing));
        assertFalse(h.canUndo());
    }

    @Test
    void clearForgetsEverything() {
        CommandHistory h = new CommandHistory();
        h.run(new Logged("a", new ArrayList<>()));
        assertTrue(h.canUndo());
        h.clear();
        assertFalse(h.canUndo());
    }
}
