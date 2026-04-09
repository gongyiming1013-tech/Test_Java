package com.rover;

/**
 * Marker action for undo. Rover detects this via {@code instanceof}
 * and restores the previous state from its history stack.
 *
 * <p>{@link #execute} should never be called directly.</p>
 */
public class UndoAction implements Action {

    @Override
    public RoverState execute(Position position, Direction direction) {
        throw new UnsupportedOperationException("UndoAction is handled by Rover, not executed directly");
    }
}
