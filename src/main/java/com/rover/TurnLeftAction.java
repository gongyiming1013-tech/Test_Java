package com.rover;

/**
 * Rotates the rover 90° counter-clockwise without changing position.
 */
public class TurnLeftAction implements Action {

    @Override
    public RoverState execute(Position position, Direction direction) {
        return new RoverState(position, direction.turnLeft());
    }
}
