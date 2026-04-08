package com.rover;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TurnLeftActionTest {

    private final Action action = new TurnLeftAction();

    @Test
    public void execute_turnsDirectionLeft() {
        RoverState state = action.execute(new Position(0, 0), Direction.NORTH);
        assertEquals(Direction.WEST, state.direction());
    }

    @Test
    public void execute_positionUnchanged() {
        Position pos = new Position(3, 5);
        RoverState state = action.execute(pos, Direction.EAST);
        assertEquals(pos, state.position());
    }
}
