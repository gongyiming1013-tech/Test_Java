package com.rover;

/**
 * Subtle, understated theme with muted colors.
 *
 * <p>Features: white rover, gray gradient trail, dim frame, yellow blocked flash.
 * Designed for users who prefer a clean, low-contrast aesthetic.</p>
 */
public class MinimalTheme implements Theme {

    /** Muted colors for multi-rover differentiation. */
    private static final int[] ROVER_PALETTE = {
            255,  // white
            174,  // soft pink
            114,  // soft green
            222,  // soft yellow
            146,  // soft purple
            180,  // soft orange
            110,  // soft blue
            252   // light gray
    };

    @Override
    public String roverColor(int roverIndex) {
        return AnsiStyle.fg256(ROVER_PALETTE[roverIndex % ROVER_PALETTE.length]);
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
        // Gradient from light gray (250) to dark gray (238)
        int range = 250 - 238;
        int step = (int) ((long) age * range / Math.max(maxAge, 1));
        step = Math.min(step, range);
        return AnsiStyle.fg256(250 - step);
    }

    @Override
    public String pathSymbol() {
        return "·";
    }

    @Override
    public String obstacleColor() {
        return AnsiStyle.fg256(243);
    }

    @Override
    public String obstacleSymbol() {
        return "#";
    }

    @Override
    public String emptySymbol() {
        return "·";
    }

    @Override
    public String borderColor() {
        return AnsiStyle.dim() + AnsiStyle.fg256(245);
    }

    @Override
    public String blockedColor() {
        return AnsiStyle.fg256(220);
    }

    @Override
    public String statusStyle() {
        return AnsiStyle.fg256(245);
    }

    @Override
    public int gradientWindow() {
        return 8;
    }
}
