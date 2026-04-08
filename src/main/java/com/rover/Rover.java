package com.rover;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
 *
 * <p>V5: Supports {@link RoverListener} observers notified after each step.</p>
 */
public class Rover {

    private final AtomicReference<RoverState> state;
    private final Environment environment;
    private final ConflictPolicy conflictPolicy;
    private final List<RoverListener> listeners = new CopyOnWriteArrayList<>();

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
     * Registers a listener to be notified of state changes.
     *
     * @param listener the listener to add
     */
    public void addListener(RoverListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(RoverListener listener) {
        listeners.remove(listener);
    }

    /**
     * Executes a single action, updating this rover's state.
     * Listeners are notified with stepIndex=0, totalSteps=1.
     *
     * @param action the action to execute
     * @throws MoveBlockedException if the move is blocked and conflict policy is FAIL
     */
    public synchronized void execute(Action action) {
        executeOne(action, 0, 1);
    }

    /**
     * Executes a single action with explicit step metadata.
     * Used by {@link StepExecutor} for paced execution with correct step tracking.
     *
     * @param action     the action to execute
     * @param stepIndex  zero-based step index in the sequence
     * @param totalSteps total number of steps in the sequence
     * @throws MoveBlockedException if the move is blocked and conflict policy is FAIL
     */
    public synchronized void execute(Action action, int stepIndex, int totalSteps) {
        executeOne(action, stepIndex, totalSteps);
    }

    /**
     * Executes a sequence of actions in order.
     * Listeners receive {@code onStep} for each action and {@code onComplete} at the end.
     *
     * @param actions the actions to execute
     * @throws MoveBlockedException if a move is blocked and conflict policy is FAIL (aborts remaining actions)
     */
    public synchronized void execute(List<Action> actions) {
        try {
            for (int i = 0; i < actions.size(); i++) {
                executeOne(actions.get(i), i, actions.size());
            }
        } finally {
            fireComplete();
        }
    }

    /**
     * Notifies all listeners that execution is complete.
     * Called automatically by {@link #execute(List)}; call manually when
     * using {@link StepExecutor} or individual {@link #execute(Action)} calls.
     */
    public void fireComplete() {
        RoverState finalState = state.get();
        for (RoverListener listener : listeners) {
            listener.onComplete(finalState);
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

    private void executeOne(Action action, int stepIndex, int totalSteps) {
        RoverState previous = state.get();
        RoverState next = action.execute(previous.position(), previous.direction());
        boolean blocked = false;
        boolean shouldThrow = false;
        Position blockedPos = null;

        if (!next.position().equals(previous.position())) {
            MoveResult result = environment.validate(previous.position(), next.position());
            if (result.blocked()) {
                blocked = true;
                switch (conflictPolicy) {
                    case FAIL -> {
                        shouldThrow = true;
                        blockedPos = next.position();
                    }
                    case SKIP -> { /* state unchanged */ }
                    case REVERSE -> state.set(new RoverState(previous.position(), previous.direction().reverse()));
                }
            } else {
                state.set(new RoverState(result.position(), next.direction()));
            }
        } else {
            state.set(next);
        }

        notifyStep(new RoverEvent(previous, state.get(), action, stepIndex, totalSteps, blocked));

        if (shouldThrow) {
            throw new MoveBlockedException(blockedPos,
                    "move blocked at " + blockedPos.x() + "," + blockedPos.y());
        }
    }

    private void notifyStep(RoverEvent event) {
        for (RoverListener listener : listeners) {
            listener.onStep(event);
        }
    }
}
