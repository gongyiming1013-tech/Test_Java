package com.rover;

/**
 * Moves the rover one step opposite to its current facing direction.
 * The facing direction remains unchanged.
 */
public class BackwardAction implements Action {

    @Override
    public RoverState execute(Position position, Direction direction) {
        Direction opposite = direction.reverse();
        Position newPos = position.move(opposite);
        return new RoverState(newPos, direction);
    }
}
