package com.rover;

/**
 * Thrown when {@link ConflictPolicy#FAIL} is active and a move is blocked
 * by a boundary (in {@link BoundaryMode#BOUNDED}) or an obstacle.
 */
public class MoveBlockedException extends RuntimeException {

    private final Position blockedPosition;
    private final String reason;

    /**
     * Creates a new exception for a blocked move.
     *
     * @param blockedPosition the position the rover attempted to move to
     * @param reason          description of why the move was blocked (e.g., "boundary", "obstacle")
     */
    public MoveBlockedException(Position blockedPosition, String reason) {
        super("Move blocked at " + blockedPosition.x() + "," + blockedPosition.y() + ": " + reason);
        this.blockedPosition = blockedPosition;
        this.reason = reason;
    }

    /** Returns the position that was blocked. */
    public Position getBlockedPosition() {
        return blockedPosition;
    }

    /** Returns the reason the move was blocked. */
    public String getReason() {
        return reason;
    }
}
