package com.rover;

/**
 * Contract for move validation. Decouples constraint logic from {@link Rover}.
 *
 * <p>Implementations check whether a proposed position is valid within
 * the environment (boundaries, obstacles) and return a {@link MoveResult}.</p>
 */
public interface Environment {

    /**
     * Validates a proposed move.
     *
     * @param current  the rover's current position
     * @param proposed the position the rover wants to move to
     * @return a {@link MoveResult} with the accepted position and whether the move was blocked
     */
    MoveResult validate(Position current, Position proposed);
}
