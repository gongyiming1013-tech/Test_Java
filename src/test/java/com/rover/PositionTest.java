package com.rover;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PositionTest {

    @Test
    public void moveNorth_incrementsY() {
        assertEquals(new Position(0, 1), new Position(0, 0).move(Direction.NORTH));
    }

    @Test
    public void moveSouth_decrementsY() {
        assertEquals(new Position(0, -1), new Position(0, 0).move(Direction.SOUTH));
    }

    @Test
    public void moveEast_incrementsX() {
        assertEquals(new Position(1, 0), new Position(0, 0).move(Direction.EAST));
    }

    @Test
    public void moveWest_decrementsX() {
        assertEquals(new Position(-1, 0), new Position(0, 0).move(Direction.WEST));
    }

    @Test
    public void moveFromNonOrigin() {
        assertEquals(new Position(3, 6), new Position(3, 5).move(Direction.NORTH));
    }

    @Test
    public void moveIntoNegativeCoordinates() {
        assertEquals(new Position(-1, 0), new Position(0, 0).move(Direction.WEST));
    }

    @Test
    public void equality() {
        assertEquals(new Position(1, 2), new Position(1, 2));
        assertNotEquals(new Position(1, 2), new Position(2, 1));
    }
}
