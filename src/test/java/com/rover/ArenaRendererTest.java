package com.rover;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Set;

import static org.junit.Assert.*;

/** Tests for {@link ArenaRenderer}. */
public class ArenaRendererTest {

    @Test
    public void render_showsBothRoversOnGrid() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        Arena arena = new Arena(new GridEnvironment(5, 5, Set.of(), BoundaryMode.BOUNDED), ConflictPolicy.SKIP);
        ArenaRenderer renderer = new ArenaRenderer(5, 5, Set.of(), arena, out);

        Rover r1 = arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        Rover r2 = arena.createRover("R2", new Position(4, 4), Direction.SOUTH);
        r1.addListener(renderer.listenerFor("R1"));
        r2.addListener(renderer.listenerFor("R2"));

        // R1 moves north
        r1.execute(new MoveForwardAction());

        String output = baos.toString();
        // A = R1 at (0,1), B = R2 at (4,4)
        assertTrue("Should contain rover A", output.contains("A"));
        assertTrue("Should contain rover B", output.contains("B"));
    }

    @Test
    public void render_showsTrailWithLowercaseLabels() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        ArenaRenderer renderer = new ArenaRenderer(5, 5, Set.of(), arena, out);

        Rover r1 = arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        r1.addListener(renderer.listenerFor("R1"));

        // Two moves — first position becomes trail
        r1.execute(new MoveForwardAction(), 0, 2);
        r1.execute(new MoveForwardAction(), 1, 2);

        String output = baos.toString();
        // 'a' trail for R1's previous positions
        assertTrue("Should contain trail 'a'", output.contains("a"));
    }

    @Test
    public void render_showsObstacles() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        Set<Position> obstacles = Set.of(new Position(2, 2));
        Arena arena = new Arena(new GridEnvironment(5, 5, obstacles, BoundaryMode.BOUNDED), ConflictPolicy.SKIP);
        ArenaRenderer renderer = new ArenaRenderer(5, 5, obstacles, arena, out);

        Rover r1 = arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        r1.addListener(renderer.listenerFor("R1"));

        r1.execute(new MoveForwardAction());

        String output = baos.toString();
        assertTrue("Should contain obstacle #", output.contains("#"));
    }

    @Test
    public void render_showsBlockedStatus() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.SKIP);
        ArenaRenderer renderer = new ArenaRenderer(5, 5, Set.of(), arena, out);

        Rover r1 = arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        Rover r2 = arena.createRover("R2", new Position(0, 1), Direction.SOUTH);
        r1.addListener(renderer.listenerFor("R1"));
        r2.addListener(renderer.listenerFor("R2"));

        r1.execute(new MoveForwardAction()); // blocked by R2

        String output = baos.toString();
        assertTrue("Should show BLOCKED", output.contains("[BLOCKED]"));
    }

    @Test
    public void render_showsStepInfo() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        ArenaRenderer renderer = new ArenaRenderer(5, 5, Set.of(), arena, out);

        Rover r1 = arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        r1.addListener(renderer.listenerFor("R1"));

        r1.execute(new MoveForwardAction());

        String output = baos.toString();
        assertTrue("Should show rover label in step info", output.contains("A(R1)"));
        assertTrue("Should show step count", output.contains("Step 1/1"));
    }

    @Test
    public void onAllComplete_printsFinalPositions() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        ArenaRenderer renderer = new ArenaRenderer(5, 5, Set.of(), arena, out);

        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(3, 3), Direction.EAST);
        renderer.listenerFor("R1");
        renderer.listenerFor("R2");

        renderer.onAllComplete();

        String output = baos.toString();
        assertTrue("Should contain R1 position", output.contains("A(R1):0,0"));
        assertTrue("Should contain R2 position", output.contains("B(R2):3,3"));
    }

    @Test
    public void render_gridBorders() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);

        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        ArenaRenderer renderer = new ArenaRenderer(3, 3, Set.of(), arena, out);

        Rover r1 = arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        r1.addListener(renderer.listenerFor("R1"));

        r1.execute(new TurnRightAction());

        String output = baos.toString();
        assertTrue(output.contains("┌"));
        assertTrue(output.contains("┘"));
    }
}
