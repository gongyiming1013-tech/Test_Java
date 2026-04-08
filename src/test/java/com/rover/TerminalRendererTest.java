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

        // Rover should appear as ▲ at position (0,1)
        assertTrue(output.contains("▲"));
        assertTrue(output.contains("Step 1/1"));
        assertTrue(output.contains("MoveForward"));
    }

    @Test
    public void onStep_rendersDirectionalArrows() {
        // Test each direction
        for (Direction dir : Direction.values()) {
            RoverState prev = new RoverState(new Position(1, 1), dir);
            RoverState next = new RoverState(new Position(1, 1), dir);
            RoverEvent event = new RoverEvent(prev, next, new TurnLeftAction(), 0, 1, false);

            String output = captureOnStep(3, 3, Set.of(), event);
            char expected = switch (dir) {
                case NORTH -> '▲';
                case SOUTH -> '▼';
                case EAST -> '►';
                case WEST -> '◄';
            };
            assertTrue("Expected " + expected + " for " + dir, output.contains(String.valueOf(expected)));
        }
    }

    @Test
    public void onStep_rendersObstacles() {
        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverEvent event = new RoverEvent(prev, next, new TurnLeftAction(), 0, 1, false);

        String output = captureOnStep(3, 3, Set.of(new Position(1, 1)), event);
        assertTrue(output.contains("#"));
    }

    @Test
    public void onStep_rendersPathTrail() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        TerminalRenderer renderer = new TerminalRenderer(3, 3, Set.of(), out);

        // Step 0: move from (0,0) to (0,1) — both positions added to visited
        RoverState prev0 = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next0 = new RoverState(new Position(0, 1), Direction.NORTH);
        renderer.onStep(new RoverEvent(prev0, next0, new MoveForwardAction(), 0, 2, false));

        // Step 1: move from (0,1) to (0,2)
        RoverState prev1 = new RoverState(new Position(0, 1), Direction.NORTH);
        RoverState next1 = new RoverState(new Position(0, 2), Direction.NORTH);
        renderer.onStep(new RoverEvent(prev1, next1, new MoveForwardAction(), 1, 2, false));

        String output = baos.toString();
        // Path trail should show ↑ for visited positions (rover was heading north)
        assertTrue(output.contains("↑"));
    }

    @Test
    public void onStep_blockedMove_showsBlockedStatus() {
        RoverState prev = new RoverState(new Position(2, 4), Direction.NORTH);
        RoverState next = new RoverState(new Position(2, 4), Direction.NORTH);
        RoverEvent event = new RoverEvent(prev, next, new MoveForwardAction(), 0, 1, true);

        String output = captureOnStep(5, 5, Set.of(), event);
        assertTrue(output.contains("[BLOCKED]"));
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
}
