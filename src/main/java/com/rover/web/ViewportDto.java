package com.rover.web;

/**
 * Inclusive 2D coordinate range describing the visible portion of the arena.
 *
 * <p>For bounded grids, this matches the grid dimensions exactly:
 * {@code (0, 0) – (width-1, height-1)}. For unbounded grids, it is computed
 * by {@link com.rover.web.ViewportCalculator#autoFit} to contain all
 * rovers, trails, obstacles, and the origin with padding.</p>
 *
 * @param xMin inclusive minimum x
 * @param yMin inclusive minimum y
 * @param xMax inclusive maximum x
 * @param yMax inclusive maximum y
 */
public record ViewportDto(int xMin, int yMin, int xMax, int yMax) {

    /**
     * Returns the number of cells spanned horizontally (inclusive).
     *
     * @return width in cells
     */
    public int width() {
        return xMax - xMin + 1;
    }

    /**
     * Returns the number of cells spanned vertically (inclusive).
     *
     * @return height in cells
     */
    public int height() {
        return yMax - yMin + 1;
    }
}
