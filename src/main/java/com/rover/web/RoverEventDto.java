package com.rover.web;

/**
 * JSON-serializable projection of a {@link com.rover.RoverEvent} with rover-id context.
 *
 * <p>Emitted as the {@code data} payload of SSE {@code step} events. Field
 * names are part of the frontend contract — changing them breaks the canvas
 * renderer.</p>
 *
 * @param roverId    identifier of the rover that produced this step
 * @param stepIndex  zero-based index in the command sequence
 * @param totalSteps total number of actions in the sequence
 * @param prevX      x coordinate before the action
 * @param prevY      y coordinate before the action
 * @param prevDir    direction name before the action ({@code "NORTH"}, etc.)
 * @param newX       x coordinate after the action
 * @param newY       y coordinate after the action
 * @param newDir     direction name after the action
 * @param action     readable action name (e.g., {@code "MoveForward"}, {@code "TurnLeft"})
 * @param blocked    whether the move was blocked by environment or collision
 */
public record RoverEventDto(
        String roverId,
        int stepIndex,
        int totalSteps,
        int prevX,
        int prevY,
        String prevDir,
        int newX,
        int newY,
        String newDir,
        String action,
        boolean blocked
) {
}
