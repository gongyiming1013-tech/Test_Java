package com.rover;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

/** Tests for {@link StepExecutor}. */
public class StepExecutorTest {

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
    public void execute_actionsInOrder_withCorrectStepInfo() {
        Rover rover = new Rover();
        RecordingListener listener = new RecordingListener();
        rover.addListener(listener);

        StepExecutor executor = new StepExecutor(rover, 0);
        executor.execute(List.of(new MoveForwardAction(), new TurnRightAction(), new MoveForwardAction()));

        assertEquals(3, listener.steps.size());
        assertEquals(0, listener.steps.get(0).stepIndex());
        assertEquals(3, listener.steps.get(0).totalSteps());
        assertEquals(1, listener.steps.get(1).stepIndex());
        assertEquals(2, listener.steps.get(2).stepIndex());

        // fireComplete called
        assertNotNull(listener.completedState);
        assertEquals(new Position(1, 1), listener.completedState.position());
    }

    @Test
    public void execute_withDelay_takesApproximateTime() {
        Rover rover = new Rover();
        StepExecutor executor = new StepExecutor(rover, 50);

        long start = System.currentTimeMillis();
        executor.execute(List.of(new MoveForwardAction(), new MoveForwardAction(), new MoveForwardAction()));
        long elapsed = System.currentTimeMillis() - start;

        // 3 actions, 2 delays of 50ms each = ~100ms minimum
        assertTrue("Expected >= 80ms but was " + elapsed, elapsed >= 80);
        assertEquals(new Position(0, 3), rover.getPosition());
    }

    @Test
    public void execute_failPolicy_fireCompleteStillCalled() {
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.BOUNDED);
        Rover rover = new Rover(new Position(0, 0), Direction.NORTH, env, ConflictPolicy.FAIL);
        RecordingListener listener = new RecordingListener();
        rover.addListener(listener);

        StepExecutor executor = new StepExecutor(rover, 0);
        try {
            executor.execute(List.of(
                    new MoveForwardAction(), new MoveForwardAction(),
                    new MoveForwardAction(), new MoveForwardAction(),
                    new MoveForwardAction() // blocked
            ));
            fail("Should have thrown");
        } catch (MoveBlockedException e) {
            assertNotNull(listener.completedState);
            assertEquals(new Position(0, 4), listener.completedState.position());
        }
    }

    @Test
    public void executeAsync_returnsCompletedFuture() throws Exception {
        Rover rover = new Rover();
        StepExecutor executor = new StepExecutor(rover, 0);

        Future<?> future = executor.executeAsync(List.of(new MoveForwardAction(), new MoveForwardAction()));
        future.get(); // wait for completion

        assertEquals(new Position(0, 2), rover.getPosition());
    }

    @Test
    public void execute_emptyList_fireCompleteStillCalled() {
        Rover rover = new Rover();
        RecordingListener listener = new RecordingListener();
        rover.addListener(listener);

        StepExecutor executor = new StepExecutor(rover, 0);
        executor.execute(List.of());

        assertEquals(0, listener.steps.size());
        assertNotNull(listener.completedState);
    }
}
