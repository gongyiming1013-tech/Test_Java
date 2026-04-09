package com.rover;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/** Tests for {@link ArenaEnvironment}. */
public class ArenaEnvironmentTest {

    @Test
    public void noCollision_delegatesToBase() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(3, 3), Direction.SOUTH);

        // R1 moves to (0,1) — no other rover there
        Rover r1 = arena.getRover("R1");
        r1.execute(new MoveForwardAction());
        assertEquals(new Position(0, 1), r1.getPosition());
    }

    @Test
    public void collision_withAnotherRover_blocked() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.SKIP);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(0, 1), Direction.SOUTH);

        // R1 tries to move north to (0,1) — R2 is there
        Rover r1 = arena.getRover("R1");
        r1.execute(new MoveForwardAction());
        assertEquals(new Position(0, 0), r1.getPosition()); // stayed put (SKIP)
    }

    @Test(expected = MoveBlockedException.class)
    public void collision_failPolicy_throwsException() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(0, 1), Direction.SOUTH);

        arena.getRover("R1").execute(new MoveForwardAction());
    }

    @Test
    public void collision_reversePolicy_reversesDirection() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.REVERSE);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(0, 1), Direction.SOUTH);

        Rover r1 = arena.getRover("R1");
        r1.execute(new MoveForwardAction());
        assertEquals(new Position(0, 0), r1.getPosition());
        assertEquals(Direction.SOUTH, r1.getDirection()); // reversed
    }

    @Test
    public void collisionCheck_excludesSelf() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);

        // R1 turns — position stays (0,0), no self-collision
        Rover r1 = arena.getRover("R1");
        r1.execute(new TurnRightAction());
        assertEquals(Direction.EAST, r1.getDirection());
    }

    @Test
    public void collision_plusBoundary_collisionCheckedFirst() {
        // 5x5 grid, R1 at (0,0), R2 at (0,1). R1 tries to move north.
        // Collision should be detected before boundary check.
        Environment grid = new GridEnvironment(5, 5, Set.of(), BoundaryMode.BOUNDED);
        Arena arena = new Arena(grid, ConflictPolicy.SKIP);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(0, 1), Direction.SOUTH);

        Rover r1 = arena.getRover("R1");
        r1.execute(new MoveForwardAction());
        assertEquals(new Position(0, 0), r1.getPosition()); // blocked by R2
    }

    @Test
    public void collision_plusObstacle_bothBlocked() {
        // Grid with obstacle at (1,0). R2 at (0,1). R1 at (0,0) facing north.
        Environment grid = new GridEnvironment(5, 5, Set.of(new Position(1, 0)), BoundaryMode.BOUNDED);
        Arena arena = new Arena(grid, ConflictPolicy.SKIP);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(0, 1), Direction.SOUTH);

        Rover r1 = arena.getRover("R1");
        // Move north → blocked by R2
        r1.execute(new MoveForwardAction());
        assertEquals(new Position(0, 0), r1.getPosition());

        // Turn east, move → blocked by obstacle at (1,0)
        r1.execute(new TurnRightAction());
        r1.execute(new MoveForwardAction());
        assertEquals(new Position(0, 0), r1.getPosition());
    }

    @Test
    public void afterRoverMoves_collisionUpdates() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.SKIP);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(0, 2), Direction.SOUTH);

        Rover r1 = arena.getRover("R1");
        // Move to (0,1) — not blocked
        r1.execute(new MoveForwardAction());
        assertEquals(new Position(0, 1), r1.getPosition());

        // Move to (0,2) — blocked by R2
        r1.execute(new MoveForwardAction());
        assertEquals(new Position(0, 1), r1.getPosition()); // stayed put

        // R2 moves away
        Rover r2 = arena.getRover("R2");
        r2.execute(new MoveForwardAction()); // R2 moves south to (0,1)... wait, R1 is there
        // R2 at (0,2) facing south → moves to (0,1) but R1 is there → blocked
        assertEquals(new Position(0, 2), r2.getPosition()); // blocked by R1
    }
}
