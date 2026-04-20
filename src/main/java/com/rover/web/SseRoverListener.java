package com.rover.web;

import com.rover.RoverEvent;
import com.rover.RoverListener;
import com.rover.RoverState;

/**
 * {@link RoverListener} adapter that broadcasts each step as an SSE
 * {@code step} event via a {@link Session}.
 *
 * <p>One instance is attached per rover for the duration of a run, so each
 * emitted event carries the correct rover ID context. Detached after the run
 * finishes (in {@code Session.runInternal}'s {@code finally}).</p>
 */
public class SseRoverListener implements RoverListener {

    private final String roverId;
    private final Session session;

    /**
     * Creates a listener bound to a rover ID and the session whose subscribers
     * should receive emitted events.
     *
     * @param roverId rover identifier to embed in each event
     * @param session session whose subscribers will be broadcast to
     */
    public SseRoverListener(String roverId, Session session) {
        this.roverId = roverId;
        this.session = session;
    }

    /**
     * Builds a {@link RoverEventDto} from the domain event and broadcasts it
     * as an SSE event named {@code "step"}.
     */
    @Override
    public void onStep(RoverEvent event) {
        RoverEventDto dto = new RoverEventDto(
                roverId,
                event.stepIndex(),
                event.totalSteps(),
                event.previousState().position().x(),
                event.previousState().position().y(),
                event.previousState().direction().name(),
                event.newState().position().x(),
                event.newState().position().y(),
                event.newState().direction().name(),
                actionLabel(event.action().getClass().getSimpleName()),
                event.blocked()
        );
        session.broadcast("step", dto);
    }

    /**
     * No-op. The terminal {@code complete} event is emitted at the
     * {@link Session} level (single emission per run, not per rover).
     */
    @Override
    public void onComplete(RoverState finalState) {
        // Intentionally empty — Session emits run-level complete.
    }

    /**
     * Translates an {@link com.rover.Action} subclass name to a stable, JSON-friendly
     * label by stripping the trailing {@code "Action"} suffix.
     *
     * @param actionClassSimpleName simple class name (e.g., {@code "MoveForwardAction"})
     * @return readable name (e.g., {@code "MoveForward"})
     */
    static String actionLabel(String actionClassSimpleName) {
        if (actionClassSimpleName.endsWith("Action")) {
            return actionClassSimpleName.substring(0, actionClassSimpleName.length() - "Action".length());
        }
        return actionClassSimpleName;
    }
}
