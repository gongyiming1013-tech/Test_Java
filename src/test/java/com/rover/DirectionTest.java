package com.rover;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DirectionTest {

    // --- turnLeft ---

    @Test
    public void turnLeftFromNorth_returnsWest() {
        assertEquals(Direction.WEST, Direction.NORTH.turnLeft());
    }

    @Test
    public void turnLeftFromWest_returnsSouth() {
        assertEquals(Direction.SOUTH, Direction.WEST.turnLeft());
    }

    @Test
    public void turnLeftFromSouth_returnsEast() {
        assertEquals(Direction.EAST, Direction.SOUTH.turnLeft());
    }

    @Test
    public void turnLeftFromEast_returnsNorth() {
        assertEquals(Direction.NORTH, Direction.EAST.turnLeft());
    }

    // --- turnRight ---

    @Test
    public void turnRightFromNorth_returnsEast() {
        assertEquals(Direction.EAST, Direction.NORTH.turnRight());
    }

    @Test
    public void turnRightFromEast_returnsSouth() {
        assertEquals(Direction.SOUTH, Direction.EAST.turnRight());
    }

    @Test
    public void turnRightFromSouth_returnsWest() {
        assertEquals(Direction.WEST, Direction.SOUTH.turnRight());
    }

    @Test
    public void turnRightFromWest_returnsNorth() {
        assertEquals(Direction.NORTH, Direction.WEST.turnRight());
    }

    // --- full rotations ---

    @Test
    public void fullLeftRotation_returnsToOriginal() {
        Direction d = Direction.NORTH;
        for (int i = 0; i < 4; i++) {
            d = d.turnLeft();
        }
        assertEquals(Direction.NORTH, d);
    }

    @Test
    public void fullRightRotation_returnsToOriginal() {
        Direction d = Direction.NORTH;
        for (int i = 0; i < 4; i++) {
            d = d.turnRight();
        }
        assertEquals(Direction.NORTH, d);
    }

    // --- dx/dy ---

    @Test
    public void dxDy_correctForEachDirection() {
        assertEquals(0, Direction.NORTH.dx());
        assertEquals(1, Direction.NORTH.dy());

        assertEquals(1, Direction.EAST.dx());
        assertEquals(0, Direction.EAST.dy());

        assertEquals(0, Direction.SOUTH.dx());
        assertEquals(-1, Direction.SOUTH.dy());

        assertEquals(-1, Direction.WEST.dx());
        assertEquals(0, Direction.WEST.dy());
    }
}
