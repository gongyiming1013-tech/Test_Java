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
 *
 * <p>V2: Supports optional {@link Environment} for move validation and
 * {@link ConflictPolicy} for handling blocked moves.</p>
 */
public class Rover {

    private final AtomicReference<RoverState> state;
    private final Environment environment;
    private final ConflictPolicy conflictPolicy;

    /** Creates a rover at the origin facing North, no constraints. */
    public Rover() {
        this(new Position(0, 0), Direction.NORTH);
    }

    /**
     * Creates a rover with the given initial state, no constraints.
     *
     * @param position  starting position
     * @param direction starting direction
     */
    public Rover(Position position, Direction direction) {
        this(position, direction, new UnboundedEnvironment(), ConflictPolicy.FAIL);
    }

    /**
     * Creates a rover with the given initial state, environment, and conflict policy.
     *
     * @param position       starting position
     * @param direction      starting direction
     * @param environment    environment for move validation
     * @param conflictPolicy how to handle blocked moves
     */
    public Rover(Position position, Direction direction, Environment environment, ConflictPolicy conflictPolicy) {
        this.state = new AtomicReference<>(new RoverState(position, direction));
        this.environment = environment;
        this.conflictPolicy = conflictPolicy;
    }

    /**
     * Executes a single action, updating this rover's state.
     *
     * <p>Synchronized to ensure the action runs exactly once, even under
     * contention — critical for actions that may have side effects.</p>
     *
     * @param action the action to execute
     * @throws MoveBlockedException if the move is blocked and conflict policy is FAIL
     */
    public synchronized void execute(Action action) {
        executeOne(action);
    }

    /**
     * Executes a sequence of actions in order.
     *
     * @param actions the actions to execute
     * @throws MoveBlockedException if a move is blocked and conflict policy is FAIL (aborts remaining actions)
     */
    public synchronized void execute(List<Action> actions) {
        for (Action action : actions) {
            executeOne(action);
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

    private void executeOne(Action action) {
        RoverState current = state.get();
        RoverState next = action.execute(current.position(), current.direction());

        // Only validate if position changed (turns don't need validation)
        if (!next.position().equals(current.position())) {
            MoveResult result = environment.validate(current.position(), next.position());
            if (result.blocked()) {
                switch (conflictPolicy) {
                    case FAIL -> throw new MoveBlockedException(next.position(),
                            "move blocked at " + next.position().x() + "," + next.position().y());
                    case SKIP -> { return; }
                    case REVERSE -> {
                        state.set(new RoverState(current.position(), current.direction().reverse()));
                        return;
                    }
                }
            }
            state.set(new RoverState(result.position(), next.direction()));
        } else {
            state.set(next);
        }
    }
}
