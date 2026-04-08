package com.rover;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ActionParserTest {

    private final ActionParser parser = new ActionParser();

    @Test
    public void parseSingleL() {
        List<Action> actions = parser.parse("L");
        assertEquals(1, actions.size());
        assertTrue(actions.get(0) instanceof TurnLeftAction);
    }

    @Test
    public void parseSingleR() {
        List<Action> actions = parser.parse("R");
        assertEquals(1, actions.size());
        assertTrue(actions.get(0) instanceof TurnRightAction);
    }

    @Test
    public void parseSingleM() {
        List<Action> actions = parser.parse("M");
        assertEquals(1, actions.size());
        assertTrue(actions.get(0) instanceof MoveForwardAction);
    }

    @Test
    public void parseMultipleActions() {
        List<Action> actions = parser.parse("LMRM");
        assertEquals(4, actions.size());
        assertTrue(actions.get(0) instanceof TurnLeftAction);
        assertTrue(actions.get(1) instanceof MoveForwardAction);
        assertTrue(actions.get(2) instanceof TurnRightAction);
        assertTrue(actions.get(3) instanceof MoveForwardAction);
    }

    @Test
    public void parseEmptyString_returnsEmptyList() {
        assertTrue(parser.parse("").isEmpty());
    }

    @Test
    public void parseNull_returnsEmptyList() {
        assertTrue(parser.parse(null).isEmpty());
    }

    @Test(expected = InvalidActionException.class)
    public void parseLowercase_throwsException() {
        parser.parse("lmr");
    }

    @Test(expected = InvalidActionException.class)
    public void parseInvalidChar_throwsException() {
        parser.parse("LXM");
    }

    @Test
    public void parseInvalidChar_messageContainsPosition() {
        try {
            parser.parse("LXM");
            fail("Expected InvalidActionException");
        } catch (InvalidActionException e) {
            assertTrue(e.getMessage().contains("position 1"));
            assertTrue(e.getMessage().contains("'X'"));
        }
    }

    @Test
    public void parseMixedInvalid_throwsOnFirst() {
        try {
            parser.parse("LM2R");
            fail("Expected InvalidActionException");
        } catch (InvalidActionException e) {
            assertTrue(e.getMessage().contains("position 2"));
            assertTrue(e.getMessage().contains("'2'"));
        }
    }

    @Test
    public void registerCustomAction_parsesSuccessfully() {
        Action customAction = (position, direction) ->
                new RoverState(new Position(position.x(), position.y() - 1), direction);

        parser.register('B', customAction);
        List<Action> actions = parser.parse("B");

        assertEquals(1, actions.size());
        RoverState result = actions.get(0).execute(new Position(0, 0), Direction.NORTH);
        assertEquals(new Position(0, -1), result.position());
    }
}
