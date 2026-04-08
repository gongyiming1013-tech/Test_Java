package com.rover;

import org.junit.Test;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

/**
 * Tests for Rover with Environment and ConflictPolicy (V2).
 */
public class RoverEnvironmentTest {

    private static final Environment GRID_5X5 =
            new GridEnvironment(5, 5, Set.of(), BoundaryMode.BOUNDED);
    private static final Environment GRID_5X5_WRAP =
            new GridEnvironment(5, 5, Set.of(), BoundaryMode.WRAP);

    // --- ConflictPolicy.FAIL ---

    @Test(expected = MoveBlockedException.class)
    public void fail_moveBeyondBoundary_throwsException() {
        Rover rover = new Rover(new Position(2, 4), Direction.NORTH, GRID_5X5, ConflictPolicy.FAIL);
        rover.execute(new MoveForwardAction());
    }

    @Test
    public void fail_sequenceAborts_onlyMovesBeforeBlockCommitted() {
        // At (0,0) facing north on 5x5 grid: MMMMM → 5th move hits wall at y=5
        Rover rover = new Rover(new Position(0, 0), Direction.NORTH, GRID_5X5, ConflictPolicy.FAIL);
        try {
            rover.execute(List.of(
                    new MoveForwardAction(), new MoveForwardAction(),
                    new MoveForwardAction(), new MoveForwardAction(),
                    new MoveForwardAction() // blocked: y=4 → y=5
            ));
            fail("Should have thrown MoveBlockedException");
        } catch (MoveBlockedException e) {
            // First 4 moves committed, 5th blocked
            assertEquals(new Position(0, 4), rover.getPosition());
            assertEquals(Direction.NORTH, rover.getDirection());
        }
    }

    @Test
    public void fail_moveIntoObstacle_throwsException() {
        Environment env = new GridEnvironment(5, 5, Set.of(new Position(0, 2)), BoundaryMode.BOUNDED);
        Rover rover = new Rover(new Position(0, 1), Direction.NORTH, env, ConflictPolicy.FAIL);
        try {
            rover.execute(new MoveForwardAction());
            fail("Should have thrown MoveBlockedException");
        } catch (MoveBlockedException e) {
            assertEquals(new Position(0, 1), rover.getPosition());
        }
    }

    // --- ConflictPolicy.SKIP ---

    @Test
    public void skip_moveBeyondBoundary_staysPut_continuesSequence() {
        // At (2,4) facing north on 5x5: M (blocked, skip) → R → M (east to 3,4)
        Rover rover = new Rover(new Position(2, 4), Direction.NORTH, GRID_5X5, ConflictPolicy.SKIP);
        rover.execute(List.of(
                new MoveForwardAction(), // blocked at boundary
                new TurnRightAction(),   // turn east
                new MoveForwardAction()  // move east
        ));
        assertEquals(new Position(3, 4), rover.getPosition());
        assertEquals(Direction.EAST, rover.getDirection());
    }

    @Test
    public void skip_multipleBlockedMoves_allSkipped() {
        // At (2,4) facing north: MMMM — all blocked, all skipped
        Rover rover = new Rover(new Position(2, 4), Direction.NORTH, GRID_5X5, ConflictPolicy.SKIP);
        rover.execute(List.of(
                new MoveForwardAction(), new MoveForwardAction(),
                new MoveForwardAction(), new MoveForwardAction()
        ));
        assertEquals(new Position(2, 4), rover.getPosition());
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    @Test
    public void skip_moveIntoObstacle_skipped() {
        Environment env = new GridEnvironment(5, 5, Set.of(new Position(0, 1)), BoundaryMode.BOUNDED);
        Rover rover = new Rover(new Position(0, 0), Direction.NORTH, env, ConflictPolicy.SKIP);
        rover.execute(List.of(
                new MoveForwardAction(), // blocked by obstacle
                new TurnRightAction(),   // turn east
                new MoveForwardAction()  // move east to (1,0)
        ));
        assertEquals(new Position(1, 0), rover.getPosition());
        assertEquals(Direction.EAST, rover.getDirection());
    }

    // --- ConflictPolicy.REVERSE ---

    @Test
    public void reverse_moveBeyondBoundary_reversesDirection() {
        // At (2,4) facing north: M (blocked, reverse to south), then M goes south
        Rover rover = new Rover(new Position(2, 4), Direction.NORTH, GRID_5X5, ConflictPolicy.REVERSE);
        rover.execute(List.of(
                new MoveForwardAction(), // blocked → direction becomes SOUTH
                new MoveForwardAction()  // move south to (2,3)
        ));
        assertEquals(new Position(2, 3), rover.getPosition());
        assertEquals(Direction.SOUTH, rover.getDirection());
    }

    @Test
    public void reverse_moveIntoObstacle_reversesDirection() {
        Environment env = new GridEnvironment(5, 5, Set.of(new Position(2, 3)), BoundaryMode.BOUNDED);
        Rover rover = new Rover(new Position(2, 2), Direction.NORTH, env, ConflictPolicy.REVERSE);
        rover.execute(new MoveForwardAction()); // blocked by obstacle → reverse
        assertEquals(new Position(2, 2), rover.getPosition());
        assertEquals(Direction.SOUTH, rover.getDirection());
    }

    @Test
    public void reverse_bouncesBetweenWalls() {
        // 3x1 grid, at (0,0) facing east: M→(1,0), M→(2,0), M (blocked, reverse west), M→(1,0)
        Environment env = new GridEnvironment(3, 1, Set.of(), BoundaryMode.BOUNDED);
        Rover rover = new Rover(new Position(0, 0), Direction.EAST, env, ConflictPolicy.REVERSE);
        rover.execute(List.of(
                new MoveForwardAction(), // (1,0)
                new MoveForwardAction(), // (2,0)
                new MoveForwardAction(), // blocked → reverse to WEST
                new MoveForwardAction()  // (1,0)
        ));
        assertEquals(new Position(1, 0), rover.getPosition());
        assertEquals(Direction.WEST, rover.getDirection());
    }

    // --- WRAP mode ---

    @Test
    public void wrap_moveBeyondBoundary_wrapsPosition() {
        Rover rover = new Rover(new Position(2, 4), Direction.NORTH, GRID_5X5_WRAP, ConflictPolicy.FAIL);
        rover.execute(new MoveForwardAction());
        assertEquals(new Position(2, 0), rover.getPosition());
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    @Test
    public void wrap_fullTraversal_wrapsMultipleTimes() {
        // 5x5 wrap, at (0,0) facing north: 12 moves → wraps at y=5, y=10 → ends at y=2
        Rover rover = new Rover(new Position(0, 0), Direction.NORTH, GRID_5X5_WRAP, ConflictPolicy.FAIL);
        for (int i = 0; i < 12; i++) {
            rover.execute(new MoveForwardAction());
        }
        assertEquals(new Position(0, 2), rover.getPosition());
    }

    @Test
    public void wrap_obstacleStillBlocks() {
        // WRAP mode with obstacle — boundaries wrap but obstacle still triggers conflict
        Environment env = new GridEnvironment(5, 5, Set.of(new Position(2, 0)), BoundaryMode.WRAP);
        Rover rover = new Rover(new Position(2, 4), Direction.NORTH, env, ConflictPolicy.SKIP);
        rover.execute(new MoveForwardAction()); // wraps to (2,0) but obstacle → skip
        assertEquals(new Position(2, 4), rover.getPosition());
    }

    // --- Backward compatibility ---

    @Test
    public void defaultConstructor_noConstraints_v1Behavior() {
        Rover rover = new Rover();
        rover.execute(List.of(
                new MoveForwardAction(), new MoveForwardAction(),
                new TurnRightAction(),
                new MoveForwardAction(), new MoveForwardAction()
        ));
        assertEquals(new Position(2, 2), rover.getPosition());
        assertEquals(Direction.EAST, rover.getDirection());
    }

    @Test
    public void twoArgConstructor_noConstraints_v1Behavior() {
        Rover rover = new Rover(new Position(5, 5), Direction.SOUTH);
        rover.execute(new MoveForwardAction());
        assertEquals(new Position(5, 4), rover.getPosition());
    }

    // --- Turns are never blocked ---

    @Test
    public void turnAtBoundary_neverBlocked() {
        Rover rover = new Rover(new Position(4, 4), Direction.NORTH, GRID_5X5, ConflictPolicy.FAIL);
        rover.execute(new TurnLeftAction());
        assertEquals(Direction.WEST, rover.getDirection());
        assertEquals(new Position(4, 4), rover.getPosition());
    }
}
