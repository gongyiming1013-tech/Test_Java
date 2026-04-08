package com.rover;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A finite grid environment with optional obstacles and configurable boundary mode.
 *
 * <p>Grid spans from {@code (0,0)} to {@code (width-1, height-1)}.
 * Position {@code (0,0)} is the bottom-left corner.</p>
 */
public class GridEnvironment implements Environment {

    private final int width;
    private final int height;
    private final Set<Position> obstacles;
    private final BoundaryMode boundaryMode;

    /**
     * Creates a grid environment.
     *
     * @param width        grid width (must be positive)
     * @param height       grid height (must be positive)
     * @param obstacles    set of blocked positions (may be empty, must not be null)
     * @param boundaryMode how grid edges are treated
     * @throws IllegalArgumentException if width/height are not positive, or obstacles are outside grid
     */
    public GridEnvironment(int width, int height, Set<Position> obstacles, BoundaryMode boundaryMode) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Grid dimensions must be positive: " + width + "x" + height);
        }
        if (obstacles == null) {
            throw new IllegalArgumentException("Obstacles set must not be null");
        }
        for (Position obs : obstacles) {
            if (obs.x() < 0 || obs.x() >= width || obs.y() < 0 || obs.y() >= height) {
                throw new IllegalArgumentException("Obstacle outside grid bounds: " + obs.x() + "," + obs.y());
            }
        }
        this.width = width;
        this.height = height;
        this.obstacles = Collections.unmodifiableSet(new HashSet<>(obstacles));
        this.boundaryMode = boundaryMode;
    }

    @Override
    public MoveResult validate(Position current, Position proposed) {
        // Check obstacle first
        if (obstacles.contains(proposed)) {
            return new MoveResult(current, true);
        }

        // Check boundary
        boolean outOfBounds = proposed.x() < 0 || proposed.x() >= width
                || proposed.y() < 0 || proposed.y() >= height;

        if (outOfBounds) {
            if (boundaryMode == BoundaryMode.WRAP) {
                Position wrapped = new Position(
                        Math.floorMod(proposed.x(), width),
                        Math.floorMod(proposed.y(), height)
                );
                // Check if wrapped position is also an obstacle
                if (obstacles.contains(wrapped)) {
                    return new MoveResult(current, true);
                }
                return new MoveResult(wrapped, false);
            } else {
                return new MoveResult(current, true);
            }
        }

        return new MoveResult(proposed, false);
    }

    /** Returns the grid width. */
    public int getWidth() {
        return width;
    }

    /** Returns the grid height. */
    public int getHeight() {
        return height;
    }

    /** Returns an unmodifiable view of the obstacle set. */
    public Set<Position> getObstacles() {
        return obstacles;
    }

    /** Returns the boundary mode. */
    public BoundaryMode getBoundaryMode() {
        return boundaryMode;
    }
}
