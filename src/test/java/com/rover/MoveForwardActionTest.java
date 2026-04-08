package com.rover;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MoveForwardActionTest {

    private final Action action = new MoveForwardAction();

    @Test
    public void execute_movesInCurrentDirection() {
        RoverState state = action.execute(new Position(0, 0), Direction.NORTH);
        assertEquals(new Position(0, 1), state.position());
    }

    @Test
    public void execute_directionUnchanged() {
        RoverState state = action.execute(new Position(0, 0), Direction.NORTH);
        assertEquals(Direction.NORTH, state.direction());
    }

    @Test
    public void execute_movesFromNonOrigin() {
        RoverState state = action.execute(new Position(2, 3), Direction.EAST);
        assertEquals(new Position(3, 3), state.position());
        assertEquals(Direction.EAST, state.direction());
    }
}
