package ca.yorku.storyteller.controller;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Keeps the executed turn commands so the most recent one can be undone. Starts empty after a load.
 *
 * <p>Features: F04 F10.
 * <p>Patterns: Command (Invoker).
 */
public class CommandHistory {

    private final Deque<StoryCommand> done = new ArrayDeque<>();

    /** Executes the command and remembers it. A command that throws is not remembered. */
    public void run(StoryCommand command) {
        command.execute();
        done.push(command);
    }

    /**
     * Undoes and forgets the most recent command.
     *
     * @return the command that was undone
     * @throws IllegalStateException if there is nothing to undo
     */
    public StoryCommand undoLast() {
        if (done.isEmpty()) {
            throw new IllegalStateException("There is nothing to undo.");
        }
        StoryCommand command = done.pop();
        command.undo();
        return command;
    }

    /** The most recent command of the given type that can still be undone, if any. */
    public <T extends StoryCommand> Optional<T> latest(Class<T> type) {
        return done.stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    /** The command {@link #undoLast()} would undo next, if any. */
    public Optional<StoryCommand> peekLast() {
        return Optional.ofNullable(done.peek());
    }

    public boolean canUndo() {
        return !done.isEmpty();
    }

    /** Forgets every command, for a new or loaded story: undo never reaches back past a load. */
    public void clear() {
        done.clear();
    }
}
