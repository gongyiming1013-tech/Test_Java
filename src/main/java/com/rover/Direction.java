package com.rover;

/**
 * Cardinal directions with movement deltas and rotation support.
 *
 * <p>Each direction stores its own (dx, dy) unit vector, enabling
 * movement calculations without conditional logic.</p>
 */
public enum Direction {

    NORTH(0, 1),
    EAST(1, 0),
    SOUTH(0, -1),
    WEST(-1, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    /** Horizontal component of this direction's unit vector. */
    public int dx() {
        return dx;
    }

    /** Vertical component of this direction's unit vector. */
    public int dy() {
        return dy;
    }

    /**
     * Returns the direction 90° counter-clockwise from this one.
     *
     * @return the left-rotated direction
     */
    public Direction turnLeft() {
        Direction[] values = values();
        return values[(ordinal() + 3) % 4];
    }

    /**
     * Returns the direction 90° clockwise from this one.
     *
     * @return the right-rotated direction
     */
    public Direction turnRight() {
        Direction[] values = values();
        return values[(ordinal() + 1) % 4];
    }
}
