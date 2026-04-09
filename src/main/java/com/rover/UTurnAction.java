package com.rover;

/**
 * Reverses the rover's direction by 180°. Position remains unchanged.
 */
public class UTurnAction implements Action {

    @Override
    public RoverState execute(Position position, Direction direction) {
        return new RoverState(position, direction.reverse());
    }
}
