package com.rover.web;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only {@link SseSink} that records every emitted event in arrival order
 * so assertions can verify content, ordering, and fan-out without a real
 * Javalin server. Supports simulated termination and on-close callbacks.
 */
public class RecordingSseSink implements SseSink {

    /** A captured event tuple. */
    public static class Event {
        public final String name;
        public final Object payload;

        Event(String name, Object payload) {
            this.name = name;
            this.payload = payload;
        }
    }

    public final List<Event> events = new ArrayList<>();
    public final List<Runnable> closeCallbacks = new ArrayList<>();

    private boolean terminated = false;
    private boolean throwOnSend = false;

    @Override
    public synchronized void sendEvent(String eventName, Object data) {
        if (throwOnSend) {
            throw new RuntimeException("simulated network failure");
        }
        if (terminated) {
            throw new IllegalStateException("sink terminated");
        }
        events.add(new Event(eventName, data));
    }

    @Override
    public synchronized boolean terminated() {
        return terminated;
    }

    @Override
    public synchronized void onClose(Runnable callback) {
        closeCallbacks.add(callback);
    }

    /** Marks this sink as closed; subsequent {@code sendEvent} will fail and {@code terminated()} returns true. */
    public synchronized void simulateClose() {
        terminated = true;
        for (Runnable cb : closeCallbacks) cb.run();
    }

    /** Causes the next {@code sendEvent} to throw, simulating a transient I/O failure. */
    public synchronized void simulateSendFailure() {
        throwOnSend = true;
    }
}
