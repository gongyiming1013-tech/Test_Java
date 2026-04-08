package com.rover;

import java.util.List;

/**
 * A rover that navigates a 2D plane by executing {@link Action} objects.
 *
 * <p>State is updated immutably through actions — each action returns
 * a new {@link RoverState} that the rover adopts.</p>
 */
public class Rover {

    private Position position;
    private Direction direction;

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
        this.position = position;
        this.direction = direction;
    }

    /**
     * Executes a single action, updating this rover's state.
     *
     * @param action the action to execute
     */
    public void execute(Action action) {
        RoverState newState = action.execute(position, direction);
        this.position = newState.position();
        this.direction = newState.direction();
    }

    /**
     * Executes a sequence of actions in order.
     *
     * @param actions the actions to execute
     */
    public void execute(List<Action> actions) {
        for (Action action : actions) {
            execute(action);
        }
    }

    /** Returns the current position. */
    public Position getPosition() {
        return position;
    }

    /** Returns the current facing direction. */
    public Direction getDirection() {
        return direction;
    }
}
