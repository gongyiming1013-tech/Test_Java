package com.rover.web;

import com.rover.Action;
import com.rover.Arena;

import java.util.List;
import java.util.Map;

/**
 * Paced, step-granular driver for {@link Arena} execution.
 *
 * <p>Mirrors {@link Arena#executeSequential(Map)} and
 * {@link Arena#executeParallel(Map)} semantics, but inserts a configurable
 * {@code Thread.sleep(delayMs)} between steps so that any {@link com.rover.RoverListener}
 * attached to the rovers (notably {@link SseRoverListener}) emits events at a
 * cadence the browser can render.</p>
 *
 * <p>Honors thread interrupt: {@link InterruptedException} is caught,
 * the interrupt flag is reset, and execution returns early so the session
 * executor shuts down cleanly.</p>
 */
public final class ArenaStepExecutor {

    private ArenaStepExecutor() {
        // Utility class
    }

    /**
     * Executes each rover's commands fully before moving to the next rover
     * (matches V4 sequential semantics). Pauses {@code delayMs} milliseconds
     * between consecutive rover steps.
     *
     * @param arena    the arena hosting the rovers
     * @param commands map of rover ID → action list
     * @param delayMs  sleep between steps; {@code 0} = no pause
     */
    public static void executeSequential(Arena arena,
                                         Map<String, List<Action>> commands,
                                         long delayMs) {
        for (Map.Entry<String, List<Action>> entry : commands.entrySet()) {
            List<Action> actions = entry.getValue();
            int total = actions.size();
            for (int i = 0; i < total; i++) {
                arena.getRover(entry.getKey()).execute(actions.get(i), i, total);
                if (delayMs > 0 && i < total - 1) {
                    if (!sleepHonoringInterrupt(delayMs)) return;
                }
            }
        }
    }

    /**
     * Executes commands round-robin: one step per rover per round, in insertion
     * order (matches V4 parallel semantics). Pauses {@code delayMs} between rounds
     * (i.e., after each full pass over all rovers), not within a round.
     *
     * @param arena    the arena hosting the rovers
     * @param commands map of rover ID → action list
     * @param delayMs  sleep between rounds; {@code 0} = no pause
     */
    public static void executeParallel(Arena arena,
                                       Map<String, List<Action>> commands,
                                       long delayMs) {
        int maxSteps = commands.values().stream().mapToInt(List::size).max().orElse(0);
        for (int step = 0; step < maxSteps; step++) {
            for (Map.Entry<String, List<Action>> entry : commands.entrySet()) {
                List<Action> actions = entry.getValue();
                if (step < actions.size()) {
                    arena.getRover(entry.getKey()).execute(actions.get(step), step, actions.size());
                }
            }
            if (delayMs > 0 && step < maxSteps - 1) {
                if (!sleepHonoringInterrupt(delayMs)) return;
            }
        }
    }

    /** Sleeps and returns false if the thread was interrupted (flag re-set). */
    private static boolean sleepHonoringInterrupt(long delayMs) {
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
