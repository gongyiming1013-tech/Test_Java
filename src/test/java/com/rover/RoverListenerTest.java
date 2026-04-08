package com.rover;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Tests for Rover observer/listener mechanism. */
public class RoverListenerTest {

    /** Simple listener that records all events. */
    private static class RecordingListener implements RoverListener {
        final List<RoverEvent> steps = new ArrayList<>();
        RoverState completedState = null;

        @Override
        public void onStep(RoverEvent event) {
            steps.add(event);
        }

        @Override
        public void onComplete(RoverState finalState) {
            completedState = finalState;
        }
    }

    @Test
    public void singleAction_notifiesOnStep() {
        Rover rover = new Rover();
        RecordingListener listener = new RecordingListener();
        rover.addListener(listener);

        rover.execute(new MoveForwardAction());

        assertEquals(1, listener.steps.size());
        RoverEvent event = listener.steps.get(0);
        assertEquals(new Position(0, 0), event.previousState().position());
        assertEquals(new Position(0, 1), event.newState().position());
        assertEquals(0, event.stepIndex());
        assertEquals(1, event.totalSteps());
        assertFalse(event.blocked());
    }

    @Test
    public void batchExecution_notifiesEachStepAndComplete() {
        Rover rover = new Rover();
        RecordingListener listener = new RecordingListener();
        rover.addListener(listener);

        rover.execute(List.of(new MoveForwardAction(), new TurnRightAction(), new MoveForwardAction()));

        assertEquals(3, listener.steps.size());

        // Step 0: move north
        assertEquals(0, listener.steps.get(0).stepIndex());
        assertEquals(3, listener.steps.get(0).totalSteps());
        assertEquals(new Position(0, 1), listener.steps.get(0).newState().position());

        // Step 1: turn right
        assertEquals(1, listener.steps.get(1).stepIndex());
        assertEquals(Direction.EAST, listener.steps.get(1).newState().direction());

        // Step 2: move east
        assertEquals(2, listener.steps.get(2).stepIndex());
        assertEquals(new Position(1, 1), listener.steps.get(2).newState().position());

        // onComplete called
        assertNotNull(listener.completedState);
        assertEquals(new Position(1, 1), listener.completedState.position());
    }

    @Test
    public void blockedMove_eventHasBlockedTrue() {
        Environment env = new GridEnvironment(5, 5, java.util.Set.of(), BoundaryMode.BOUNDED);
        Rover rover = new Rover(new Position(2, 4), Direction.NORTH, env, ConflictPolicy.SKIP);
        RecordingListener listener = new RecordingListener();
        rover.addListener(listener);

        rover.execute(new MoveForwardAction());

        assertEquals(1, listener.steps.size());
        assertTrue(listener.steps.get(0).blocked());
        assertEquals(new Position(2, 4), listener.steps.get(0).newState().position());
    }

    @Test
    public void failPolicy_notifiesBeforeThrowing() {
        Environment env = new GridEnvironment(5, 5, java.util.Set.of(), BoundaryMode.BOUNDED);
        Rover rover = new Rover(new Position(2, 4), Direction.NORTH, env, ConflictPolicy.FAIL);
        RecordingListener listener = new RecordingListener();
        rover.addListener(listener);

        try {
            rover.execute(new MoveForwardAction());
            fail("Should have thrown");
        } catch (MoveBlockedException e) {
            // Listener was notified before the exception
            assertEquals(1, listener.steps.size());
            assertTrue(listener.steps.get(0).blocked());
        }
    }

    @Test
    public void multipleListeners_allNotified() {
        Rover rover = new Rover();
        RecordingListener listener1 = new RecordingListener();
        RecordingListener listener2 = new RecordingListener();
        rover.addListener(listener1);
        rover.addListener(listener2);

        rover.execute(new MoveForwardAction());

        assertEquals(1, listener1.steps.size());
        assertEquals(1, listener2.steps.size());
    }

    @Test
    public void removeListener_stopsNotifications() {
        Rover rover = new Rover();
        RecordingListener listener = new RecordingListener();
        rover.addListener(listener);

        rover.execute(new MoveForwardAction());
        assertEquals(1, listener.steps.size());

        rover.removeListener(listener);
        rover.execute(new MoveForwardAction());
        assertEquals(1, listener.steps.size()); // still 1, not notified again
    }

    @Test
    public void noListeners_noError() {
        Rover rover = new Rover();
        rover.execute(new MoveForwardAction()); // no listeners, no exception
        assertEquals(new Position(0, 1), rover.getPosition());
    }

    @Test
    public void fireComplete_notifiesListeners() {
        Rover rover = new Rover();
        RecordingListener listener = new RecordingListener();
        rover.addListener(listener);

        rover.execute(new MoveForwardAction());
        assertNull(listener.completedState); // single execute doesn't auto-complete

        rover.fireComplete();
        assertNotNull(listener.completedState);
        assertEquals(new Position(0, 1), listener.completedState.position());
    }

    @Test
    public void batchFail_onCompleteStillCalled() {
        Environment env = new GridEnvironment(5, 5, java.util.Set.of(), BoundaryMode.BOUNDED);
        Rover rover = new Rover(new Position(0, 0), Direction.NORTH, env, ConflictPolicy.FAIL);
        RecordingListener listener = new RecordingListener();
        rover.addListener(listener);

        try {
            rover.execute(List.of(
                    new MoveForwardAction(), new MoveForwardAction(),
                    new MoveForwardAction(), new MoveForwardAction(),
                    new MoveForwardAction() // blocked at y=5
            ));
            fail("Should have thrown");
        } catch (MoveBlockedException e) {
            // onComplete still called (via finally)
            assertNotNull(listener.completedState);
            assertEquals(new Position(0, 4), listener.completedState.position());
            // 5 onStep calls (4 successful + 1 blocked)
            assertEquals(5, listener.steps.size());
            assertFalse(listener.steps.get(3).blocked());
            assertTrue(listener.steps.get(4).blocked());
        }
    }
}
