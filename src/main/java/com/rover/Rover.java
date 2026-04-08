package com.rover;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A rover that navigates a 2D plane by executing {@link Action} objects.
 *
 * <p>Thread-safety strategy (hybrid):
 * <ul>
 *   <li><strong>Reads</strong> — lock-free via {@link AtomicReference#get()},
 *       safe for high-throughput concurrent observers.</li>
 *   <li><strong>Writes</strong> — {@code synchronized} on this instance,
 *       guaranteeing each action executes exactly once (safe for actions
 *       with side effects).</li>
 * </ul>
 */
public class Rover {

    private final AtomicReference<RoverState> state;

    /** Creates a rover at the origin facing North. */
    public Rover() {
        this(new Position(0, 0), Direction.NORTH);
    }

    /**
     * Creates a rover with the given initial state.
     *
     * @param position  starting position
     * @param direction starting direction
     */
    public Rover(Position position, Direction direction) {
        this.state = new AtomicReference<>(new RoverState(position, direction));
    }

    /**
     * Executes a single action, updating this rover's state.
     *
     * <p>Synchronized to ensure the action runs exactly once, even under
     * contention — critical for actions that may have side effects.</p>
     *
     * @param action the action to execute
     */
    public synchronized void execute(Action action) {
        RoverState current = state.get();
        RoverState next = action.execute(current.position(), current.direction());
        state.set(next);
    }

    /**
     * Executes a sequence of actions in order.
     *
     * @param actions the actions to execute
     */
    public synchronized void execute(List<Action> actions) {
        for (Action action : actions) {
            RoverState current = state.get();
            RoverState next = action.execute(current.position(), current.direction());
            state.set(next);
        }
    }

    /**
     * Returns an immutable snapshot of the rover's current state.
     * Lock-free — safe for concurrent readers.
     *
     * @return current state snapshot
     */
    public RoverState getState() {
        return state.get();
    }

    /** Returns the current position (lock-free read). */
    public Position getPosition() {
        return state.get().position();
    }

    /** Returns the current facing direction (lock-free read). */
    public Direction getDirection() {
        return state.get().direction();
    }
}
