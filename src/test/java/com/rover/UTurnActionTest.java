package com.rover;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Tests for {@link UTurnAction}. */
public class UTurnActionTest {

    @Test
    public void uturn_facingNorth_facesSouth() {
        RoverState result = new UTurnAction().execute(new Position(1, 1), Direction.NORTH);
        assertEquals(new Position(1, 1), result.position());
        assertEquals(Direction.SOUTH, result.direction());
    }

    @Test
    public void uturn_facingEast_facesWest() {
        RoverState result = new UTurnAction().execute(new Position(1, 1), Direction.EAST);
        assertEquals(Direction.WEST, result.direction());
    }

    @Test
    public void uturn_facingSouth_facesNorth() {
        RoverState result = new UTurnAction().execute(new Position(1, 1), Direction.SOUTH);
        assertEquals(Direction.NORTH, result.direction());
    }

    @Test
    public void uturn_facingWest_facesEast() {
        RoverState result = new UTurnAction().execute(new Position(1, 1), Direction.WEST);
        assertEquals(Direction.EAST, result.direction());
    }

    @Test
    public void uturn_positionUnchanged() {
        RoverState result = new UTurnAction().execute(new Position(3, 7), Direction.NORTH);
        assertEquals(new Position(3, 7), result.position());
    }
}
