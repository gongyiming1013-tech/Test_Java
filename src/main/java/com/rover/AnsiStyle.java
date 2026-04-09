package com.rover;

/**
 * Utility class for ANSI escape sequence generation.
 *
 * <p>Centralizes all terminal escape codes so no raw sequences appear elsewhere
 * in the codebase. Supports 256-color palette, text styles, and cursor control.</p>
 */
public final class AnsiStyle {

    private AnsiStyle() {
        // Utility class — not instantiable
    }

    /**
     * Returns the ANSI escape sequence for a 256-color foreground.
     *
     * @param code color code (0–255)
     * @return escape sequence string
     */
    public static String fg256(int code) {
        return "\033[38;5;" + code + "m";
    }

    /**
     * Returns the ANSI escape sequence for a 256-color background.
     *
     * @param code color code (0–255)
     * @return escape sequence string
     */
    public static String bg256(int code) {
        return "\033[48;5;" + code + "m";
    }

    /**
     * Returns the ANSI escape sequence for bold text.
     *
     * @return escape sequence string
     */
    public static String bold() {
        return "\033[1m";
    }

    /**
     * Returns the ANSI escape sequence for dim/faint text.
     *
     * @return escape sequence string
     */
    public static String dim() {
        return "\033[2m";
    }

    /**
     * Returns the ANSI escape sequence to reset all styles.
     *
     * @return escape sequence string
     */
    public static String reset() {
        return "\033[0m";
    }

    /**
     * Returns the ANSI escape sequence to move the cursor to the top-left corner.
     *
     * @return escape sequence string
     */
    public static String cursorHome() {
        return "\033[H";
    }

    /**
     * Returns the ANSI escape sequence to hide the cursor.
     *
     * @return escape sequence string
     */
    public static String hideCursor() {
        return "\033[?25l";
    }

    /**
     * Returns the ANSI escape sequence to show the cursor.
     *
     * @return escape sequence string
     */
    public static String showCursor() {
        return "\033[?25h";
    }
}
