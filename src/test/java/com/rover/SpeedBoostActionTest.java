package com.rover;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.assertEquals;

/** Tests for {@link SpeedBoostAction}. */
public class SpeedBoostActionTest {

    @Test
    public void boost_facingNorth_movesTwoCellsNorth() {
        RoverState result = new SpeedBoostAction().execute(new Position(0, 0), Direction.NORTH);
        assertEquals(new Position(0, 2), result.position());
        assertEquals(Direction.NORTH, result.direction());
    }

    @Test
    public void boost_facingEast_movesTwoCellsEast() {
        RoverState result = new SpeedBoostAction().execute(new Position(0, 0), Direction.EAST);
        assertEquals(new Position(2, 0), result.position());
        assertEquals(Direction.EAST, result.direction());
    }

    @Test
    public void boost_facingSouth_movesTwoCellsSouth() {
        RoverState result = new SpeedBoostAction().execute(new Position(0, 5), Direction.SOUTH);
        assertEquals(new Position(0, 3), result.position());
        assertEquals(Direction.SOUTH, result.direction());
    }

    @Test
    public void boost_facingWest_movesTwoCellsWest() {
        RoverState result = new SpeedBoostAction().execute(new Position(5, 0), Direction.WEST);
        assertEquals(new Position(3, 0), result.position());
        assertEquals(Direction.WEST, result.direction());
    }

    @Test
    public void boost_jumpsOverObstacle() {
        // Obstacle at (0,1), boost from (0,0) north → lands at (0,2), skipping obstacle
        Environment env = new GridEnvironment(5, 5, Set.of(new Position(0, 1)), BoundaryMode.BOUNDED);
        Rover rover = new Rover(new Position(0, 0), Direction.NORTH, env, ConflictPolicy.FAIL);
        rover.execute(new SpeedBoostAction());
        assertEquals(new Position(0, 2), rover.getPosition());
    }

    @Test
    public void boost_intoBoundary_blocked() {
        // 5x5 grid, at (0,3) facing north → boost to (0,5) out of bounds
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.BOUNDED);
        Rover rover = new Rover(new Position(0, 3), Direction.NORTH, env, ConflictPolicy.SKIP);
        rover.execute(new SpeedBoostAction());
        assertEquals(new Position(0, 3), rover.getPosition()); // blocked, stayed put
    }
}
