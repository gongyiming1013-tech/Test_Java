package com.rover;

/**
 * Wraps a base {@link Environment} and adds dynamic rover collision detection.
 *
 * <p>Checks whether the proposed position is occupied by another rover
 * (via {@link Arena#isOccupied}) before delegating to the base environment
 * for boundary/obstacle validation.</p>
 */
public class ArenaEnvironment implements Environment {

    private final Environment base;
    private final Arena arena;

    /**
     * Creates an arena-aware environment.
     *
     * @param base  the underlying environment (grid/obstacles/unbounded)
     * @param arena the arena for dynamic rover position queries
     */
    public ArenaEnvironment(Environment base, Arena arena) {
        this.base = base;
        this.arena = arena;
    }

    @Override
    public MoveResult validate(Position current, Position proposed) {
        // Check rover collision first
        if (arena.isOccupied(proposed, current)) {
            return new MoveResult(current, true);
        }
        // Delegate to base environment for boundary/obstacle checks
        return base.validate(current, proposed);
    }
}
