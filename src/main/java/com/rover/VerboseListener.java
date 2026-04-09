package com.rover;

import java.io.PrintStream;

/**
 * Text-only step reporter. Prints each step to the output stream on one line,
 * without the grid visual. Useful for debugging and scripts that parse per-step output.
 */
public class VerboseListener implements RoverListener {

    private final PrintStream out;

    /** Creates a verbose listener writing to {@code System.out}. */
    public VerboseListener() {
        this(System.out);
    }

    /**
     * Creates a verbose listener with a custom output stream (useful for testing).
     *
     * @param out output stream
     */
    public VerboseListener(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onStep(RoverEvent event) {
        String actionName = event.action().getClass().getSimpleName().replace("Action", "");
        String blocked = event.blocked() ? " [BLOCKED]" : "";
        out.printf("Step %d/%d: %s -> (%d,%d) %s%s%n",
                event.stepIndex() + 1, event.totalSteps(), actionName,
                event.newState().position().x(), event.newState().position().y(),
                event.newState().direction(), blocked);
    }

    @Override
    public void onComplete(RoverState finalState) {
        // no-op — the final "x:y" line is printed by the caller to preserve
        // backward compatibility with non-verbose output format.
    }
}
