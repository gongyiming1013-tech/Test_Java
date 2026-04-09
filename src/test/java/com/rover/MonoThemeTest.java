package com.rover;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests specific to {@link MonoTheme} — verifies V5b-compatible output
 * with no ANSI color codes.
 */
public class MonoThemeTest {

    private final MonoTheme theme = new MonoTheme();

    @Test
    public void roverColor_returnsEmptyString() {
        assertEquals("", theme.roverColor(0));
        assertEquals("", theme.roverColor(1));
        assertEquals("", theme.roverColor(99));
    }

    @Test
    public void pathColor_returnsEmptyString() {
        assertEquals("", theme.pathColor(0, 10));
        assertEquals("", theme.pathColor(5, 10));
        assertEquals("", theme.pathColor(10, 10));
    }

    @Test
    public void obstacleColor_returnsEmptyString() {
        assertEquals("", theme.obstacleColor());
    }

    @Test
    public void borderColor_returnsEmptyString() {
        assertEquals("", theme.borderColor());
    }

    @Test
    public void blockedColor_returnsEmptyString() {
        assertEquals("", theme.blockedColor());
    }

    @Test
    public void statusStyle_returnsEmptyString() {
        assertEquals("", theme.statusStyle());
    }

    @Test
    public void obstacleSymbol_isPlainHash() {
        assertEquals("#", theme.obstacleSymbol());
    }

    @Test
    public void emptySymbol_isPlainDot() {
        assertEquals(".", theme.emptySymbol());
    }

    @Test
    public void noAnsiCodesInAnyOutput() {
        // Verify nothing contains ESC character
        String[] outputs = {
                theme.roverColor(0),
                theme.pathColor(0, 10),
                theme.obstacleColor(),
                theme.borderColor(),
                theme.blockedColor(),
                theme.statusStyle()
        };
        for (String output : outputs) {
            assertFalse("MonoTheme should produce no ANSI codes", output.contains("\033"));
        }
    }
}
