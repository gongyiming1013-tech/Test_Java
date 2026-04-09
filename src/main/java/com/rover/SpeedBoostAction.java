package com.rover;

/**
 * Moves the rover two steps forward in its current facing direction.
 * Acts as a "jump" — only the final position is validated by the environment,
 * so intermediate cells (including obstacles) are skipped.
 */
public class SpeedBoostAction implements Action {

    @Override
    public RoverState execute(Position position, Direction direction) {
        Position newPos = new Position(
                position.x() + direction.dx() * 2,
                position.y() + direction.dy() * 2
        );
        return new RoverState(newPos, direction);
    }
}
