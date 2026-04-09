package com.rover;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.*;

/** Tests for App --verbose CLI flag (integration via main). */
public class AppVerboseTest {

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream baos;

    @Before
    public void captureOut() {
        baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
    }

    @After
    public void restoreOut() {
        System.setOut(originalOut);
    }

    @Test
    public void main_verboseFlag_printsStepsAndFinal() {
        App.main(new String[]{"--verbose", "MMRM"});
        String output = baos.toString();

        assertTrue("Should contain step 1", output.contains("Step 1/4"));
        assertTrue("Should contain step 4", output.contains("Step 4/4"));
        assertTrue("Should contain MoveForward", output.contains("MoveForward"));
        assertTrue("Should contain TurnRight", output.contains("TurnRight"));
        // Last line should be "x:y" — final rover position
        assertTrue("Should end with final coords", output.trim().endsWith("1:2"));
    }

    @Test
    public void main_verboseWithGrid_printsSteps() {
        App.main(new String[]{"--grid", "5x5", "--verbose", "MMM"});
        String output = baos.toString();

        assertTrue(output.contains("Step 1/3"));
        assertTrue(output.contains("Step 3/3"));
        assertTrue(output.trim().endsWith("0:3"));
    }

    @Test
    public void main_verboseWithFailAndBlocked_catchesException() {
        // 3x3 grid, move 4 times north — 4th move would exceed boundary → MoveBlockedException
        App.main(new String[]{"--grid", "3x3", "--on-conflict", "fail", "--verbose", "MMMM"});
        String output = baos.toString();

        // Should contain the "Execution stopped" message from the catch block
        assertTrue("Should print Execution stopped message", output.contains("Execution stopped"));
        // And still print final position at end
        assertTrue("Should still end with rover position", output.trim().endsWith("0:2"));
    }

    @Test
    public void main_verboseWithSkipPolicy_showsBlockedStep() {
        App.main(new String[]{"--grid", "3x3", "--on-conflict", "skip", "--verbose", "MMMM"});
        String output = baos.toString();

        assertTrue("Should show blocked step", output.contains("[BLOCKED]"));
        assertTrue(output.trim().endsWith("0:2"));
    }

    @Test
    public void main_verboseWithObstacles() {
        App.main(new String[]{
                "--grid", "5x5",
                "--obstacles", "0,2",
                "--on-conflict", "skip",
                "--verbose", "MMM"
        });
        String output = baos.toString();

        assertTrue(output.contains("[BLOCKED]"));
    }

    // --- Coverage for edge branches ---

    @Test
    public void main_emptyObstaclesString_treatedAsNoObstacles() {
        // Exercises the spec.isEmpty() branch of parseObstacles
        App.main(new String[]{"--grid", "5x5", "--obstacles", "", "MMM"});
        assertTrue(baos.toString().trim().endsWith("0:3"));
    }

    @Test
    public void resolveTheme_nullTerm_returnsMonoTheme() {
        assertTrue(App.resolveTheme(null, null) instanceof MonoTheme);
    }

    @Test
    public void resolveTheme_dumbTerm_returnsMonoTheme() {
        assertTrue(App.resolveTheme(null, "dumb") instanceof MonoTheme);
    }

    @Test
    public void resolveTheme_unknownTerm_returnsMonoTheme() {
        assertTrue(App.resolveTheme(null, "unknown") instanceof MonoTheme);
    }

    @Test
    public void resolveTheme_colorTerm_returnsModernTheme() {
        assertTrue(App.resolveTheme(null, "xterm-256color") instanceof ModernTheme);
    }

    @Test
    public void resolveTheme_explicitNameIgnoresTerm() {
        // When name is provided, TERM is irrelevant
        assertTrue(App.resolveTheme("modern", "dumb") instanceof ModernTheme);
        assertTrue(App.resolveTheme("mono", "xterm-256color") instanceof MonoTheme);
    }
}
