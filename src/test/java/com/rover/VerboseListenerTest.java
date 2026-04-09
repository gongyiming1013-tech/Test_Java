package com.rover;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** Tests for {@link VerboseListener}. */
public class VerboseListenerTest {

    private ByteArrayOutputStream baos;
    private PrintStream out;

    private void setUp() {
        baos = new ByteArrayOutputStream();
        out = new PrintStream(baos);
    }

    @Test
    public void onStep_printsStepInfoOnSingleLine() {
        setUp();
        VerboseListener listener = new VerboseListener(out);
        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 1), Direction.NORTH);
        RoverEvent event = new RoverEvent(prev, next, new MoveForwardAction(), 0, 3, false);

        listener.onStep(event);

        String output = baos.toString();
        assertTrue(output.contains("Step 1/3"));
        assertTrue(output.contains("MoveForward"));
        assertTrue(output.contains("(0,1)"));
        assertTrue(output.contains("NORTH"));
        assertFalse(output.contains("BLOCKED"));
    }

    @Test
    public void onStep_blockedMove_showsBlockedTag() {
        setUp();
        VerboseListener listener = new VerboseListener(out);
        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverEvent event = new RoverEvent(prev, next, new MoveForwardAction(), 0, 1, true);

        listener.onStep(event);

        String output = baos.toString();
        assertTrue(output.contains("[BLOCKED]"));
    }

    @Test
    public void onComplete_isNoOp() {
        setUp();
        VerboseListener listener = new VerboseListener(out);
        listener.onComplete(new RoverState(new Position(5, 5), Direction.EAST));
        assertEquals("", baos.toString());
    }

    @Test
    public void fullSequence_printsAllSteps() {
        setUp();
        Rover rover = new Rover();
        rover.addListener(new VerboseListener(out));

        ActionParser parser = new ActionParser();
        rover.execute(parser.parse("MMRM"));

        String output = baos.toString();
        assertTrue(output.contains("Step 1/4"));
        assertTrue(output.contains("Step 2/4"));
        assertTrue(output.contains("Step 3/4"));
        assertTrue(output.contains("Step 4/4"));
        assertTrue(output.contains("MoveForward"));
        assertTrue(output.contains("TurnRight"));
    }

    @Test
    public void blockedMoveInSequence_showsBlocked() {
        setUp();
        Environment env = new GridEnvironment(3, 3, Set.of(), BoundaryMode.BOUNDED);
        Rover rover = new Rover(new Position(0, 2), Direction.NORTH, env, ConflictPolicy.SKIP);
        rover.addListener(new VerboseListener(out));

        rover.execute(List.of(new MoveForwardAction()));

        String output = baos.toString();
        assertTrue(output.contains("[BLOCKED]"));
    }

    @Test
    public void defaultConstructor_usesSystemOut() {
        // Just verify it doesn't throw
        VerboseListener listener = new VerboseListener();
        assertNotNull(listener);
    }
}
