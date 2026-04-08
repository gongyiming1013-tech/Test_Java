package com.rover;

/**
 * Defines how the rover reacts when a move is blocked by a boundary or obstacle.
 */
public enum ConflictPolicy {

    /** Rover stays put; throws {@link MoveBlockedException}; command sequence aborts. */
    FAIL,

    /** Rover stays put; blocked move silently skipped; continues remaining commands. */
    SKIP,

    /** Rover turns 180° (reverses direction); stays at current position; continues remaining commands. */
    REVERSE
}
