package com.rover;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** Tests for undo/redo functionality in Rover. */
public class UndoRedoTest {

    // --- Undo basic ---

    @Test
    public void undo_singleMove_restoresPreviousState() {
        Rover rover = new Rover();
        rover.execute(new MoveForwardAction()); // (0,1)
        rover.execute(new UndoAction());         // back to (0,0)
        assertEquals(new Position(0, 0), rover.getPosition());
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    @Test
    public void undo_multipleTimes_walkBackToOrigin() {
        Rover rover = new Rover();
        rover.execute(new MoveForwardAction()); // (0,1)
        rover.execute(new MoveForwardAction()); // (0,2)
        rover.execute(new TurnRightAction());   // (0,2) EAST
        rover.execute(new UndoAction());         // (0,2) NORTH
        rover.execute(new UndoAction());         // (0,1) NORTH
        rover.execute(new UndoAction());         // (0,0) NORTH
        assertEquals(new Position(0, 0), rover.getPosition());
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    @Test
    public void undo_emptyHistory_noOp() {
        Rover rover = new Rover();
        rover.execute(new UndoAction()); // nothing to undo
        assertEquals(new Position(0, 0), rover.getPosition());
    }

    @Test
    public void undo_afterTurn_restoresDirection() {
        Rover rover = new Rover();
        rover.execute(new TurnRightAction()); // EAST
        rover.execute(new UndoAction());       // back to NORTH
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    // --- Redo basic ---

    @Test
    public void redo_afterUndo_restoresUndoneState() {
        Rover rover = new Rover();
        rover.execute(new MoveForwardAction()); // (0,1)
        rover.execute(new UndoAction());         // (0,0)
        rover.execute(new RedoAction());          // (0,1)
        assertEquals(new Position(0, 1), rover.getPosition());
    }

    @Test
    public void redo_multipleUndoRedo() {
        Rover rover = new Rover();
        rover.execute(new MoveForwardAction()); // (0,1)
        rover.execute(new MoveForwardAction()); // (0,2)
        rover.execute(new UndoAction());         // (0,1)
        rover.execute(new UndoAction());         // (0,0)
        rover.execute(new RedoAction());          // (0,1)
        rover.execute(new RedoAction());          // (0,2)
        assertEquals(new Position(0, 2), rover.getPosition());
    }

    @Test
    public void redo_emptyStack_noOp() {
        Rover rover = new Rover();
        rover.execute(new RedoAction()); // nothing to redo
        assertEquals(new Position(0, 0), rover.getPosition());
    }

    @Test
    public void redo_clearedByNewAction() {
        Rover rover = new Rover();
        rover.execute(new MoveForwardAction()); // (0,1)
        rover.execute(new UndoAction());         // (0,0) — redo has (0,1)
        rover.execute(new TurnRightAction());    // new action → clears redo
        rover.execute(new RedoAction());          // nothing to redo
        assertEquals(new Position(0, 0), rover.getPosition());
        assertEquals(Direction.EAST, rover.getDirection());
    }

    // --- Mixed command strings ---

    @Test
    public void commandString_MMZRM() {
        // M→(0,1), M→(0,2), Z→(0,1), R→EAST, M→(1,1)
        Rover rover = new Rover();
        ActionParser parser = new ActionParser();
        rover.execute(parser.parse("MMZRM"));
        assertEquals(new Position(1, 1), rover.getPosition());
        assertEquals(Direction.EAST, rover.getDirection());
    }

    @Test
    public void commandString_MMZY() {
        // M→(0,1), M→(0,2), Z→(0,1), Y→(0,2)
        Rover rover = new Rover();
        ActionParser parser = new ActionParser();
        rover.execute(parser.parse("MMZY"));
        assertEquals(new Position(0, 2), rover.getPosition());
    }

    @Test
    public void commandString_withNewActions_MBSUM() {
        // M→(0,1), B→(0,0) facing N, S→(0,2), U→facing S, M→(0,1)
        Rover rover = new Rover();
        ActionParser parser = new ActionParser();
        rover.execute(parser.parse("MBSUM"));
        assertEquals(new Position(0, 1), rover.getPosition());
        assertEquals(Direction.SOUTH, rover.getDirection());
    }

    // --- Undo with environment constraints ---

    @Test
    public void undo_blockedMove_notInHistory() {
        // Blocked move (SKIP) doesn't change state → not in history → undo goes to previous real move
        Environment env = new GridEnvironment(5, 5, Set.of(new Position(0, 2)), BoundaryMode.BOUNDED);
        Rover rover = new Rover(new Position(0, 0), Direction.NORTH, env, ConflictPolicy.SKIP);
        rover.execute(new MoveForwardAction()); // (0,1)
        rover.execute(new MoveForwardAction()); // blocked at (0,2), stays (0,1)
        rover.execute(new UndoAction());         // undo last real change → (0,0)
        assertEquals(new Position(0, 0), rover.getPosition());
    }

    // --- Listener integration ---

    @Test
    public void undo_notifiesListener() {
        Rover rover = new Rover();
        List<RoverEvent> events = new ArrayList<>();
        rover.addListener(new RoverListener() {
            @Override public void onStep(RoverEvent event) { events.add(event); }
            @Override public void onComplete(RoverState finalState) {}
        });

        rover.execute(new MoveForwardAction());
        rover.execute(new UndoAction());

        assertEquals(2, events.size());
        RoverEvent undoEvent = events.get(1);
        assertEquals(new Position(0, 1), undoEvent.previousState().position());
        assertEquals(new Position(0, 0), undoEvent.newState().position());
        assertFalse(undoEvent.blocked());
    }

    // --- UndoAction/RedoAction execute() throws ---

    @Test(expected = UnsupportedOperationException.class)
    public void undoAction_directExecute_throws() {
        new UndoAction().execute(new Position(0, 0), Direction.NORTH);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void redoAction_directExecute_throws() {
        new RedoAction().execute(new Position(0, 0), Direction.NORTH);
    }
}
