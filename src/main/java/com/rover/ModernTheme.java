package com.rover;

/**
 * Default colorful theme using ANSI 256-color palette.
 *
 * <p>Features: cyan rover, blue→gray gradient trail, white box-drawing frame,
 * red blocked flash, green progress bar. Multi-rover mode cycles through
 * a palette of distinct bright colors.</p>
 */
public class ModernTheme implements Theme {

    /** Bright colors for multi-rover differentiation. */
    private static final int[] ROVER_PALETTE = {
            51,   // cyan
            196,  // red
            46,   // green
            226,  // yellow
            201,  // magenta
            208,  // orange
            21,   // blue
            231   // white
    };

    /** Gradient from bright cyan (51) through medium blue to dim gray (240). */
    private static final int[] TRAIL_GRADIENT = {
            51, 44, 38, 32, 26, 25, 24, 244, 242, 240
    };

    @Override
    public String roverColor(int roverIndex) {
        return AnsiStyle.bold() + AnsiStyle.fg256(ROVER_PALETTE[roverIndex % ROVER_PALETTE.length]);
    }

    @Override
    public String roverSymbol(Direction direction) {
        return switch (direction) {
            case NORTH -> "▲";
            case SOUTH -> "▼";
            case EAST  -> "►";
            case WEST  -> "◄";
        };
    }

    @Override
    public String pathColor(int age, int maxAge) {
        int index = (int) ((long) age * (TRAIL_GRADIENT.length - 1) / Math.max(maxAge, 1));
        index = Math.min(index, TRAIL_GRADIENT.length - 1);
        return AnsiStyle.fg256(TRAIL_GRADIENT[index]);
    }

    @Override
    public String pathSymbol() {
        return "•";
    }

    @Override
    public String obstacleColor() {
        return AnsiStyle.fg256(124);
    }

    @Override
    public String obstacleSymbol() {
        return "█";
    }

    @Override
    public String emptySymbol() {
        return "·";
    }

    @Override
    public String borderColor() {
        return AnsiStyle.fg256(255);
    }

    @Override
    public String blockedColor() {
        return AnsiStyle.bold() + AnsiStyle.fg256(196);
    }

    @Override
    public String statusStyle() {
        return AnsiStyle.fg256(252);
    }

    @Override
    public int gradientWindow() {
        return 10;
    }
}
