package com.rover;

/**
 * An environment with no constraints. All moves are valid.
 *
 * <p>Used as the default when no grid is specified, preserving V1 behavior.</p>
 */
public class UnboundedEnvironment implements Environment {

    @Override
    public MoveResult validate(Position current, Position proposed) {
        return new MoveResult(proposed, false);
    }
}
