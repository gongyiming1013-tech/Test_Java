package com.rover;

/**
 * Rotates the rover 90° clockwise without changing position.
 */
public class TurnRightAction implements Action {

    @Override
    public RoverState execute(Position position, Direction direction) {
        return new RoverState(position, direction.turnRight());
    }
}
