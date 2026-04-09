package com.rover;

/**
 * Marker action for redo. Rover detects this via {@code instanceof}
 * and re-applies the most recently undone state from its redo stack.
 *
 * <p>{@link #execute} should never be called directly.</p>
 */
public class RedoAction implements Action {

    @Override
    public RoverState execute(Position position, Direction direction) {
        throw new UnsupportedOperationException("RedoAction is handled by Rover, not executed directly");
    }
}
