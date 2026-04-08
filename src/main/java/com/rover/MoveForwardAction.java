package com.rover;

/**
 * Moves the rover one step in its current facing direction.
 */
public class MoveForwardAction implements Action {

    @Override
    public RoverState execute(Position position, Direction direction) {
        return new RoverState(position.move(direction), direction);
    }
}
