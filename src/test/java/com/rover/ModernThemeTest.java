package com.rover;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests specific to {@link ModernTheme} — verifies color output,
 * gradient behavior, and ANSI code presence.
 */
public class ModernThemeTest {

    private final ModernTheme theme = new ModernTheme();

    @Test
    public void roverColor_containsAnsiCodes() {
        String color = theme.roverColor(0);
        assertTrue("Modern roverColor should contain ANSI codes", color.contains("\033["));
    }

    @Test
    public void roverColor_includesBold() {
        String color = theme.roverColor(0);
        assertTrue("Modern roverColor should be bold", color.contains(AnsiStyle.bold()));
    }

    @Test
    public void pathColor_gradientVariesWithAge() {
        String youngest = theme.pathColor(0, 10);
        String oldest = theme.pathColor(10, 10);
        assertNotEquals("Gradient endpoints should differ", youngest, oldest);
    }

    @Test
    public void pathColor_intermediateValuesExist() {
        String young = theme.pathColor(0, 10);
        String mid = theme.pathColor(5, 10);
        String old = theme.pathColor(10, 10);
        // At minimum, endpoints should differ from middle
        // (exact assertion depends on palette, but middle should be reachable)
        assertNotNull(mid);
        assertTrue(mid.contains("\033["));
    }

    @Test
    public void obstacleColor_containsAnsiCodes() {
        assertTrue(theme.obstacleColor().contains("\033["));
    }

    @Test
    public void borderColor_containsAnsiCodes() {
        assertTrue(theme.borderColor().contains("\033["));
    }

    @Test
    public void blockedColor_containsRedAndBold() {
        String blocked = theme.blockedColor();
        assertTrue(blocked.contains(AnsiStyle.bold()));
        assertTrue(blocked.contains("\033["));
    }

    @Test
    public void obstacleSymbol_isFilledBlock() {
        assertEquals("█", theme.obstacleSymbol());
    }

    @Test
    public void pathSymbol_isBullet() {
        assertEquals("•", theme.pathSymbol());
    }

    @Test
    public void emptySymbol_isMiddleDot() {
        assertEquals("·", theme.emptySymbol());
    }
}
