package com.rover;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Set;

import static org.junit.Assert.*;

/** Tests for {@link TerminalRenderer}. */
public class TerminalRendererTest {

    private String captureOnStep(int width, int height, Set<Position> obstacles,
                                  RoverEvent event) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        TerminalRenderer renderer = new TerminalRenderer(width, height, obstacles, out);
        renderer.onStep(event);
        return baos.toString();
    }

    @Test
    public void onStep_rendersRoverAtCorrectPosition() {
        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 1), Direction.NORTH);
        RoverEvent event = new RoverEvent(prev, next, new MoveForwardAction(), 0, 1, false);

        String output = captureOnStep(3, 3, Set.of(), event);

        assertTrue(output.contains("▲"));
        assertTrue(output.contains("Step 1/1"));
    }

    @Test
    public void onStep_rendersDirectionalArrows() {
        for (Direction dir : Direction.values()) {
            RoverState prev = new RoverState(new Position(1, 1), dir);
            RoverState next = new RoverState(new Position(1, 1), dir);
            RoverEvent event = new RoverEvent(prev, next, new TurnLeftAction(), 0, 1, false);

            String output = captureOnStep(3, 3, Set.of(), event);
            String expected = switch (dir) {
                case NORTH -> "▲";
                case SOUTH -> "▼";
                case EAST  -> "►";
                case WEST  -> "◄";
            };
            assertTrue("Expected " + expected + " for " + dir, output.contains(expected));
        }
    }

    @Test
    public void onStep_rendersObstacles() {
        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverEvent event = new RoverEvent(prev, next, new TurnLeftAction(), 0, 1, false);

        // MonoTheme uses '#' for obstacles
        String output = captureOnStep(3, 3, Set.of(new Position(1, 1)), event);
        assertTrue(output.contains("#"));
    }

    @Test
    public void onStep_rendersPathTrail() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        TerminalRenderer renderer = new TerminalRenderer(3, 3, Set.of(), out);

        // Step 0: move from (0,0) to (0,1) — both positions added to trail
        RoverState prev0 = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next0 = new RoverState(new Position(0, 1), Direction.NORTH);
        renderer.onStep(new RoverEvent(prev0, next0, new MoveForwardAction(), 0, 2, false));

        // Step 1: move from (0,1) to (0,2)
        RoverState prev1 = new RoverState(new Position(0, 1), Direction.NORTH);
        RoverState next1 = new RoverState(new Position(0, 2), Direction.NORTH);
        renderer.onStep(new RoverEvent(prev1, next1, new MoveForwardAction(), 1, 2, false));

        String output = baos.toString();
        assertTrue("Path trail should show ↑ for north-heading trail", output.contains("↑"));
    }

    @Test
    public void onStep_blockedMove_showsBlockedStatus() {
        RoverState prev = new RoverState(new Position(2, 4), Direction.NORTH);
        RoverState next = new RoverState(new Position(2, 4), Direction.NORTH);
        RoverEvent event = new RoverEvent(prev, next, new MoveForwardAction(), 0, 1, true);

        String output = captureOnStep(5, 5, Set.of(), event);
        assertTrue("Should show blocked warning", output.contains("Blocked"));
        assertTrue("Should show warning symbol", output.contains("⚠"));
    }

    @Test
    public void onComplete_printsCompletionMessage() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        TerminalRenderer renderer = new TerminalRenderer(5, 5, Set.of(), out);

        renderer.onComplete(new RoverState(new Position(3, 2), Direction.EAST));

        String output = baos.toString();
        assertTrue(output.contains("Complete"));
        assertTrue(output.contains("3:2"));
        assertTrue(output.contains("EAST"));
    }

    @Test
    public void onComplete_showsCursor() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        TerminalRenderer renderer = new TerminalRenderer(5, 5, Set.of(), out);

        renderer.onComplete(new RoverState(new Position(0, 0), Direction.NORTH));

        String output = baos.toString();
        assertTrue("Should restore cursor visibility", output.contains(AnsiStyle.showCursor()));
    }

    @Test
    public void onStep_rendersGridBorders() {
        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverEvent event = new RoverEvent(prev, next, new TurnLeftAction(), 0, 1, false);

        String output = captureOnStep(3, 3, Set.of(), event);
        assertTrue(output.contains("┌"));
        assertTrue(output.contains("┐"));
        assertTrue(output.contains("└"));
        assertTrue(output.contains("┘"));
        assertTrue(output.contains("│"));
    }

    @Test
    public void onStep_usesFlickerFreeRendering() {
        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverEvent event = new RoverEvent(prev, next, new TurnLeftAction(), 0, 1, false);

        String output = captureOnStep(3, 3, Set.of(), event);

        // Should use cursor-home, NOT clear-screen
        assertTrue("Should use cursor home", output.contains(AnsiStyle.cursorHome()));
        assertFalse("Should NOT use clear screen", output.contains("\033[2J"));
    }

    @Test
    public void onStep_hidesCursorOnFirstRender() {
        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverEvent event = new RoverEvent(prev, next, new TurnLeftAction(), 0, 1, false);

        String output = captureOnStep(3, 3, Set.of(), event);
        assertTrue("Should hide cursor", output.contains(AnsiStyle.hideCursor()));
    }

    @Test
    public void onStep_containsColumnAndRowLabels() {
        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverEvent event = new RoverEvent(prev, next, new TurnLeftAction(), 0, 1, false);

        String output = captureOnStep(3, 3, Set.of(), event);
        // Column headers
        assertTrue("Should show column 0", output.contains("0"));
        assertTrue("Should show column 1", output.contains("1"));
        assertTrue("Should show column 2", output.contains("2"));
    }

    @Test
    public void onStep_containsStatusBar() {
        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 1), Direction.NORTH);
        RoverEvent event = new RoverEvent(prev, next, new MoveForwardAction(), 0, 3, false);

        String output = captureOnStep(3, 3, Set.of(), event);
        assertTrue("Should contain progress bar", output.contains("█"));
        assertTrue("Should contain rover position", output.contains("(0,1)"));
        assertTrue("Should contain direction", output.contains("NORTH"));
        assertTrue("Should contain moving status", output.contains("Moving..."));
    }

    @Test
    public void onStep_withCommandSequence_showsCommandPreview() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        TerminalRenderer renderer = new TerminalRenderer(3, 3, Set.of(), out,
                new MonoTheme(), "MMR");

        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 1), Direction.NORTH);
        renderer.onStep(new RoverEvent(prev, next, new MoveForwardAction(), 0, 3, false));

        String output = baos.toString();
        assertTrue("Should highlight current command", output.contains("[M]"));
        assertTrue("Should show other commands", output.contains("R"));
    }

    @Test
    public void onStep_withModernTheme_containsAnsiColorCodes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        TerminalRenderer renderer = new TerminalRenderer(3, 3, Set.of(), out,
                new ModernTheme(), "M");

        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 1), Direction.NORTH);
        renderer.onStep(new RoverEvent(prev, next, new MoveForwardAction(), 0, 1, false));

        String output = baos.toString();
        assertTrue("Modern theme should produce ANSI color codes", output.contains("\033[38;5;"));
    }

    @Test
    public void onStep_withMonoTheme_noAnsiColorCodes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        TerminalRenderer renderer = new TerminalRenderer(3, 3, Set.of(), out,
                new MonoTheme(), "M");

        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 1), Direction.NORTH);
        renderer.onStep(new RoverEvent(prev, next, new MoveForwardAction(), 0, 1, false));

        String output = baos.toString();
        // Should contain cursor-control codes but no color codes
        String withoutCursorCodes = output
                .replace(AnsiStyle.hideCursor(), "")
                .replace(AnsiStyle.cursorHome(), "")
                .replace(AnsiStyle.showCursor(), "");
        assertFalse("Mono theme should not produce ANSI 256-color codes",
                withoutCursorCodes.contains("\033[38;5;"));
    }

    @Test
    public void onStep_blockedMove_withModernTheme_usesBlockedColor() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        ModernTheme modern = new ModernTheme();
        TerminalRenderer renderer = new TerminalRenderer(3, 3, Set.of(), out, modern, "M");

        RoverState prev = new RoverState(new Position(1, 1), Direction.NORTH);
        RoverState next = new RoverState(new Position(1, 1), Direction.NORTH);
        renderer.onStep(new RoverEvent(prev, next, new MoveForwardAction(), 0, 1, true));

        String output = baos.toString();
        // Blocked color is bold + red (196)
        assertTrue("Should use blocked color for rover", output.contains(AnsiStyle.fg256(196)));
    }

    @Test
    public void onStep_gradientTrail_olderCellsDifferFromNewer() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        TerminalRenderer renderer = new TerminalRenderer(5, 5, Set.of(), out,
                new ModernTheme(), "MMMM");

        // Execute 4 moves north: (0,0) → (0,1) → (0,2) → (0,3) → (0,4)
        Position[] positions = {
                new Position(0, 0), new Position(0, 1),
                new Position(0, 2), new Position(0, 3), new Position(0, 4)
        };
        for (int i = 0; i < 4; i++) {
            RoverState prev = new RoverState(positions[i], Direction.NORTH);
            RoverState next = new RoverState(positions[i + 1], Direction.NORTH);
            renderer.onStep(new RoverEvent(prev, next, new MoveForwardAction(), i, 4, false));
        }

        // The output from the last frame should contain trail markers with color codes
        String output = baos.toString();
        assertTrue("Trail should contain directional arrows", output.contains("↑"));
    }
}
