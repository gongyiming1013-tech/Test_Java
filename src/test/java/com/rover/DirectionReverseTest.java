package com.rover;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Tests for {@link Direction#reverse()}. */
public class DirectionReverseTest {

    @Test
    public void northReversesToSouth() {
        assertEquals(Direction.SOUTH, Direction.NORTH.reverse());
    }

    @Test
    public void eastReversesToWest() {
        assertEquals(Direction.WEST, Direction.EAST.reverse());
    }

    @Test
    public void southReversesToNorth() {
        assertEquals(Direction.NORTH, Direction.SOUTH.reverse());
    }

    @Test
    public void westReversesToEast() {
        assertEquals(Direction.EAST, Direction.WEST.reverse());
    }
}
