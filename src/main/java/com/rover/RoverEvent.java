package com.rover;

/**
 * Immutable snapshot of a single step execution, emitted to {@link RoverListener}s.
 *
 * @param previousState state before the action
 * @param newState      state after the action
 * @param action        the action that was executed
 * @param stepIndex     zero-based index in the command sequence
 * @param totalSteps    total number of actions in the sequence
 * @param blocked       whether the move was blocked by the environment
 */
public record RoverEvent(
        RoverState previousState,
        RoverState newState,
        Action action,
        int stepIndex,
        int totalSteps,
        boolean blocked
) {
}
