package com.rover;

/**
 * Result of an environment validation check.
 *
 * @param position the accepted position (proposed if valid, wrapped if WRAP mode)
 * @param blocked  {@code true} if the move was blocked (boundary in BOUNDED mode, or obstacle)
 */
public record MoveResult(Position position, boolean blocked) {
}
