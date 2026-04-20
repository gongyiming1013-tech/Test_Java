package com.rover.web;

/**
 * Minimal abstraction over a Server-Sent Events client connection.
 *
 * <p>Decouples {@link Session} broadcast logic from Javalin's concrete
 * {@code SseClient} so that subscription mechanics (add, remove, fan-out,
 * fail-on-terminated) can be unit-tested without spinning up a real server.</p>
 *
 * <p>Production: {@link JavalinSseSink} adapts a Javalin {@code SseClient}.
 * Tests: a recording fake captures emitted events.</p>
 */
public interface SseSink {

    /**
     * Sends a named SSE event with a JSON-serializable payload.
     *
     * @param eventName SSE {@code event:} field (e.g., {@code "step"}, {@code "complete"}, {@code "state"})
     * @param data      payload to be serialized as the event's {@code data:} field
     */
    void sendEvent(String eventName, Object data);

    /**
     * Returns {@code true} if the underlying client connection has been closed
     * by the browser or the server. Terminated sinks should be removed from the
     * subscriber list at the next broadcast opportunity.
     *
     * @return whether the sink is no longer usable
     */
    boolean terminated();

    /**
     * Registers a callback invoked when the underlying client connection closes.
     * Used by {@link Session} to auto-unsubscribe disconnected browsers.
     *
     * @param callback runnable to invoke on close
     */
    void onClose(Runnable callback);
}
