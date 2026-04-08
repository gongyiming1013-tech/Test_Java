package com.rover;

/**
 * Strategy interface for rover actions.
 *
 * <p>Each implementation encapsulates a single transformation of
 * rover state. New actions can be added without modifying existing
 * code — simply implement this interface and register it in
 * {@link ActionParser}.</p>
 */
public interface Action {

    /**
     * Executes this action against the given state.
     *
     * @param position  the rover's current position
     * @param direction the rover's current facing direction
     * @return the new rover state after the action
     */
    RoverState execute(Position position, Direction direction);
}
