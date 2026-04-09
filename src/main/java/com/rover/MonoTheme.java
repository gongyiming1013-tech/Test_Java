package com.rover;

/**
 * Monochrome theme with no ANSI color codes.
 *
 * <p>Uses plain ASCII symbols only. Equivalent to V5b output. Serves as
 * the fallback for terminals that do not support 256-color mode
 * (e.g., {@code TERM=dumb}).</p>
 */
public class MonoTheme implements Theme {

    /** Labels for multi-rover differentiation (no color, just distinct characters). */
    private static final String[] ROVER_LABELS = {
            "A", "B", "C", "D", "E", "F", "G", "H"
    };

    @Override
    public String roverColor(int roverIndex) {
        return "";
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
        return "";
    }

    @Override
    public String pathSymbol() {
        return "·";
    }

    @Override
    public String obstacleColor() {
        return "";
    }

    @Override
    public String obstacleSymbol() {
        return "#";
    }

    @Override
    public String emptySymbol() {
        return ".";
    }

    @Override
    public String borderColor() {
        return "";
    }

    @Override
    public String blockedColor() {
        return "";
    }

    @Override
    public String statusStyle() {
        return "";
    }

    @Override
    public int gradientWindow() {
        return 10;
    }
}
