package com.rover;

/**
 * Immutable 2D coordinate on an infinite plane.
 *
 * @param x horizontal coordinate
 * @param y vertical coordinate
 */
public record Position(int x, int y) {

    /**
     * Returns a new position one step in the given direction.
     *
     * @param direction the direction to move
     * @return the resulting position
     */
    public Position move(Direction direction) {
        return new Position(x + direction.dx(), y + direction.dy());
    }
}
