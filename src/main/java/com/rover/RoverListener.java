package com.rover;

/**
 * Observer interface for rover state changes.
 *
 * <p>Implementations are notified after each action execution and when
 * a command sequence completes. Listeners must not block — keep
 * processing lightweight or buffer for async rendering.</p>
 */
public interface RoverListener {

    /**
     * Called after each action execution.
     *
     * @param event the step execution snapshot
     */
    void onStep(RoverEvent event);

    /**
     * Called when an entire command sequence finishes (success or failure).
     *
     * @param finalState the rover's state at completion
     */
    void onComplete(RoverState finalState);
}
