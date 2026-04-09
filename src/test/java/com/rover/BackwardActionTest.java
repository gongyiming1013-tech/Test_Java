package com.rover;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Tests for {@link BackwardAction}. */
public class BackwardActionTest {

    @Test
    public void backward_facingNorth_movesSouth() {
        RoverState result = new BackwardAction().execute(new Position(0, 2), Direction.NORTH);
        assertEquals(new Position(0, 1), result.position());
        assertEquals(Direction.NORTH, result.direction()); // facing preserved
    }

    @Test
    public void backward_facingEast_movesWest() {
        RoverState result = new BackwardAction().execute(new Position(3, 0), Direction.EAST);
        assertEquals(new Position(2, 0), result.position());
        assertEquals(Direction.EAST, result.direction());
    }

    @Test
    public void backward_facingSouth_movesNorth() {
        RoverState result = new BackwardAction().execute(new Position(0, 0), Direction.SOUTH);
        assertEquals(new Position(0, 1), result.position());
        assertEquals(Direction.SOUTH, result.direction());
    }

    @Test
    public void backward_facingWest_movesEast() {
        RoverState result = new BackwardAction().execute(new Position(0, 0), Direction.WEST);
        assertEquals(new Position(1, 0), result.position());
        assertEquals(Direction.WEST, result.direction());
    }
}
