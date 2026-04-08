package com.rover;

/**
 * Immutable snapshot of a rover's position and direction.
 *
 * <p>Used as the return type of {@link Action#execute} to decouple
 * actions from the Rover class itself.</p>
 *
 * @param position  current coordinates
 * @param direction current facing direction
 */
public record RoverState(Position position, Direction direction) {
}
