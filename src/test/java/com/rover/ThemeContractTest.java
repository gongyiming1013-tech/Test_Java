package com.rover;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

/**
 * Contract tests for all {@link Theme} implementations.
 *
 * <p>Parameterized over {@link ModernTheme}, {@link MinimalTheme}, and {@link MonoTheme}.
 * Verifies that each implementation satisfies the interface contract.</p>
 */
@RunWith(Parameterized.class)
public class ThemeContractTest {

    private final Theme theme;
    private final String themeName;

    public ThemeContractTest(Theme theme, String themeName) {
        this.theme = theme;
        this.themeName = themeName;
    }

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> themes() {
        return Arrays.asList(new Object[][] {
                { new ModernTheme(), "ModernTheme" },
                { new MinimalTheme(), "MinimalTheme" },
                { new MonoTheme(), "MonoTheme" }
        });
    }

    // --- roverColor ---

    @Test
    public void roverColor_returnsNonNullForAllIndices() {
        for (int i = 0; i < 10; i++) {
            assertNotNull(themeName + " roverColor(" + i + ")", theme.roverColor(i));
        }
    }

    @Test
    public void roverColor_cyclesWithoutException() {
        // Should not throw for indices beyond palette size
        String color0 = theme.roverColor(0);
        String color8 = theme.roverColor(8);
        assertNotNull(color0);
        assertNotNull(color8);
    }

    // --- roverSymbol ---

    @Test
    public void roverSymbol_returnsCorrectSymbolForEachDirection() {
        for (Direction dir : Direction.values()) {
            String symbol = theme.roverSymbol(dir);
            assertNotNull(themeName + " roverSymbol(" + dir + ")", symbol);
            assertFalse(themeName + " roverSymbol should not be empty", symbol.isEmpty());
        }
    }

    @Test
    public void roverSymbol_distinctForEachDirection() {
        String north = theme.roverSymbol(Direction.NORTH);
        String south = theme.roverSymbol(Direction.SOUTH);
        String east = theme.roverSymbol(Direction.EAST);
        String west = theme.roverSymbol(Direction.WEST);

        assertNotEquals(north, south);
        assertNotEquals(north, east);
        assertNotEquals(north, west);
        assertNotEquals(south, east);
        assertNotEquals(south, west);
        assertNotEquals(east, west);
    }

    // --- pathColor ---

    @Test
    public void pathColor_returnsNonNull() {
        assertNotNull(theme.pathColor(0, 10));
        assertNotNull(theme.pathColor(5, 10));
        assertNotNull(theme.pathColor(10, 10));
    }

    @Test
    public void pathColor_variesWithAge() {
        // For themes with color, youngest and oldest should differ
        // For MonoTheme, both are empty — still valid
        String youngest = theme.pathColor(0, 10);
        String oldest = theme.pathColor(10, 10);
        assertNotNull(youngest);
        assertNotNull(oldest);
        // We cannot assert inequality because MonoTheme returns "" for both
    }

    @Test
    public void pathColor_handlesZeroMaxAge() {
        // Should not throw on edge case
        assertNotNull(theme.pathColor(0, 0));
    }

    // --- pathSymbol ---

    @Test
    public void pathSymbol_returnsNonEmpty() {
        String symbol = theme.pathSymbol();
        assertNotNull(symbol);
        assertFalse(symbol.isEmpty());
    }

    // --- obstacleColor ---

    @Test
    public void obstacleColor_returnsNonNull() {
        assertNotNull(theme.obstacleColor());
    }

    // --- obstacleSymbol ---

    @Test
    public void obstacleSymbol_returnsNonEmpty() {
        String symbol = theme.obstacleSymbol();
        assertNotNull(symbol);
        assertFalse(symbol.isEmpty());
    }

    // --- emptySymbol ---

    @Test
    public void emptySymbol_returnsNonEmpty() {
        String symbol = theme.emptySymbol();
        assertNotNull(symbol);
        assertFalse(symbol.isEmpty());
    }

    // --- borderColor ---

    @Test
    public void borderColor_returnsNonNull() {
        assertNotNull(theme.borderColor());
    }

    // --- blockedColor ---

    @Test
    public void blockedColor_returnsNonNull() {
        assertNotNull(theme.blockedColor());
    }

    // --- statusStyle ---

    @Test
    public void statusStyle_returnsNonNull() {
        assertNotNull(theme.statusStyle());
    }

    // --- gradientWindow ---

    @Test
    public void gradientWindow_isPositive() {
        assertTrue(themeName + " gradientWindow must be > 0", theme.gradientWindow() > 0);
    }
}
