package com.rover.web;

import com.rover.Position;

import java.util.Collection;

/**
 * Pure static helper that computes viewports for both bounded and
 * unbounded arenas.
 *
 * <p>Centralizing the logic here keeps the server and any future client
 * implementations consistent, and makes the math easy to unit-test.</p>
 */
public final class ViewportCalculator {

    /** Padding (in cells) added on each side of the auto-fit bounding box. */
    static final int PADDING = 2;

    /** Minimum viewport width/height (before padding), enforced for aesthetics. */
    static final int MIN_SIZE = 10;

    private ViewportCalculator() {
        // Utility class
    }

    /**
     * Returns the viewport for a bounded grid.
     *
     * @param width  grid width (must be positive)
     * @param height grid height (must be positive)
     * @return viewport covering {@code (0, 0)} to {@code (width-1, height-1)}
     */
    public static ViewportDto forBoundedGrid(int width, int height) {
        return new ViewportDto(0, 0, width - 1, height - 1);
    }

    /**
     * Computes an auto-fit viewport for an unbounded arena. The viewport is
     * the bounding box of the given points plus padding, always including
     * the origin {@code (0, 0)} and never smaller than the minimum window.
     *
     * @param points rovers + trails + obstacles (may be empty)
     * @return auto-fit viewport
     */
    public static ViewportDto autoFit(Collection<Position> points) {
        // Always include the origin as a sentinel point
        int xMin = 0, yMin = 0, xMax = 0, yMax = 0;
        for (Position p : points) {
            if (p.x() < xMin) xMin = p.x();
            if (p.y() < yMin) yMin = p.y();
            if (p.x() > xMax) xMax = p.x();
            if (p.y() > yMax) yMax = p.y();
        }

        // Add padding on each side
        xMin -= PADDING;
        yMin -= PADDING;
        xMax += PADDING;
        yMax += PADDING;

        // Enforce minimum window size, centered on the current bbox center
        int width = xMax - xMin + 1;
        int height = yMax - yMin + 1;
        if (width < MIN_SIZE) {
            int grow = MIN_SIZE - width;
            int growLeft = grow / 2;
            int growRight = grow - growLeft;
            xMin -= growLeft;
            xMax += growRight;
        }
        if (height < MIN_SIZE) {
            int grow = MIN_SIZE - height;
            int growDown = grow / 2;
            int growUp = grow - growDown;
            yMin -= growDown;
            yMax += growUp;
        }

        return new ViewportDto(xMin, yMin, xMax, yMax);
    }
}
