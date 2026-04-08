package com.rover;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Executes actions one at a time with a configurable delay between steps.
 *
 * <p>Designed for visual mode: feeds actions to a {@link Rover} individually
 * so that registered {@link RoverListener}s can render each step.
 * The delay gives humans time to observe the animation.</p>
 */
public class StepExecutor {

    private final Rover rover;
    private final long delayMs;

    /**
     * Creates a step executor.
     *
     * @param rover   the rover to control
     * @param delayMs milliseconds to sleep between steps (0 = no delay)
     */
    public StepExecutor(Rover rover, long delayMs) {
        this.rover = rover;
        this.delayMs = delayMs;
    }

    /**
     * Executes all actions sequentially with delay between steps.
     * Calls {@link Rover#fireComplete()} when done (even on failure).
     *
     * @param actions the actions to execute
     * @throws MoveBlockedException if a move is blocked and conflict policy is FAIL
     */
    public void execute(List<Action> actions) {
        try {
            for (int i = 0; i < actions.size(); i++) {
                rover.execute(actions.get(i), i, actions.size());
                if (i < actions.size() - 1 && delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            rover.fireComplete();
        }
    }

    /**
     * Executes all actions on a background thread.
     *
     * @param actions the actions to execute
     * @return a Future representing the async execution
     */
    public Future<?> executeAsync(List<Action> actions) {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<?> future = exec.submit(() -> execute(actions));
        exec.shutdown();
        return future;
    }
}
