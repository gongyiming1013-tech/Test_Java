package com.rover;

/**
 * Defines the full visual vocabulary for terminal rendering.
 *
 * <p>Implementations supply colors, symbols, and layout parameters.
 * The renderer delegates all visual decisions to the theme, enabling
 * easy swapping between visual styles (e.g., colorful, minimal, monochrome).</p>
 */
public interface Theme {

    /**
     * Returns the ANSI color string for the nth rover (multi-rover mode).
     * Implementations should cycle the palette for indices beyond the palette size.
     *
     * @param roverIndex zero-based rover index
     * @return ANSI escape sequence for the rover's color
     */
    String roverColor(int roverIndex);

    /**
     * Returns the directional symbol for the rover's current facing.
     *
     * @param direction the rover's facing direction
     * @return a single character string (e.g., "▲", "▼", "◄", "►")
     */
    String roverSymbol(Direction direction);

    /**
     * Returns the ANSI color for a path trail cell based on its age.
     * Younger cells (lower age) should be brighter; older cells dimmer.
     *
     * @param age    number of steps since the rover visited this cell (0 = most recent)
     * @param maxAge the gradient window size from {@link #gradientWindow()}
     * @return ANSI escape sequence for the trail color
     */
    String pathColor(int age, int maxAge);

    /**
     * Returns the symbol used for path trail cells.
     *
     * @return trail symbol (e.g., "·", "•")
     */
    String pathSymbol();

    /**
     * Returns the ANSI color for obstacle cells.
     *
     * @return ANSI escape sequence for obstacle color
     */
    String obstacleColor();

    /**
     * Returns the symbol used for obstacle cells.
     *
     * @return obstacle symbol (e.g., "█", "#")
     */
    String obstacleSymbol();

    /**
     * Returns the symbol used for empty grid cells.
     *
     * @return empty cell symbol (e.g., "·", ".")
     */
    String emptySymbol();

    /**
     * Returns the ANSI color for the grid frame border.
     *
     * @return ANSI escape sequence for border color
     */
    String borderColor();

    /**
     * Returns the ANSI color for highlighting a blocked move.
     *
     * @return ANSI escape sequence for the blocked-move flash
     */
    String blockedColor();

    /**
     * Returns the ANSI color/style for the status bar text.
     *
     * @return ANSI escape sequence for status text
     */
    String statusStyle();

    /**
     * Returns the number of recent steps over which the path trail gradient is applied.
     * Cells older than this window are rendered with the dimmest trail color.
     *
     * @return gradient window size (positive integer)
     */
    int gradientWindow();
}
