package com.rover;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TurnRightActionTest {

    private final Action action = new TurnRightAction();

    @Test
    public void execute_turnsDirectionRight() {
        RoverState state = action.execute(new Position(0, 0), Direction.NORTH);
        assertEquals(Direction.EAST, state.direction());
    }

    @Test
    public void execute_positionUnchanged() {
        Position pos = new Position(3, 5);
        RoverState state = action.execute(pos, Direction.SOUTH);
        assertEquals(pos, state.position());
    }
}
