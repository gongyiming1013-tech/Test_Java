package com.rover;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.assertEquals;

public class RoverTest {

    @Test
    public void defaultState_atOriginFacingNorth() {
        Rover rover = new Rover();
        assertEquals(new Position(0, 0), rover.getPosition());
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    @Test
    public void execute_turnLeft() {
        Rover rover = new Rover();
        rover.execute(new TurnLeftAction());
        assertEquals(new Position(0, 0), rover.getPosition());
        assertEquals(Direction.WEST, rover.getDirection());
    }

    @Test
    public void execute_turnRight() {
        Rover rover = new Rover();
        rover.execute(new TurnRightAction());
        assertEquals(new Position(0, 0), rover.getPosition());
        assertEquals(Direction.EAST, rover.getDirection());
    }

    @Test
    public void execute_moveForward() {
        Rover rover = new Rover();
        rover.execute(new MoveForwardAction());
        assertEquals(new Position(0, 1), rover.getPosition());
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    @Test
    public void execute_moveAfterTurn() {
        Rover rover = new Rover();
        rover.execute(new TurnRightAction());
        rover.execute(new MoveForwardAction());
        assertEquals(new Position(1, 0), rover.getPosition());
        assertEquals(Direction.EAST, rover.getDirection());
    }

    @Test
    public void executeSequence_multipleActions() {
        // MMRMM: Move north 2, turn right, move east 2 → (2,2)
        Rover rover = new Rover();
        rover.execute(List.of(
                new MoveForwardAction(),
                new MoveForwardAction(),
                new TurnRightAction(),
                new MoveForwardAction(),
                new MoveForwardAction()
        ));
        assertEquals(new Position(2, 2), rover.getPosition());
        assertEquals(Direction.EAST, rover.getDirection());
    }

    @Test
    public void executeSequence_emptyList() {
        Rover rover = new Rover();
        rover.execute(List.of());
        assertEquals(new Position(0, 0), rover.getPosition());
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    @Test
    public void executeSequence_fullSquare() {
        // MRMRMRMR: walk a unit square, return to origin facing north
        Rover rover = new Rover();
        ActionParser parser = new ActionParser();
        rover.execute(parser.parse("MRMRMRMR"));
        assertEquals(new Position(0, 0), rover.getPosition());
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    @Test
    public void executeSequence_longPath() {
        // MMRMMLM: north 2, right, east 2, left, north 1 → (2,3)
        Rover rover = new Rover();
        ActionParser parser = new ActionParser();
        rover.execute(parser.parse("MMRMMLM"));
        assertEquals(new Position(2, 3), rover.getPosition());
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    @Test
    public void customConstructor_setsState() {
        Rover rover = new Rover(new Position(5, -3), Direction.SOUTH);
        assertEquals(new Position(5, -3), rover.getPosition());
        assertEquals(Direction.SOUTH, rover.getDirection());
    }
}
