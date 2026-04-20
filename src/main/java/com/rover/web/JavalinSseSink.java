package com.rover.web;

import io.javalin.http.sse.SseClient;

/**
 * Production {@link SseSink} that adapts a Javalin {@link SseClient}.
 *
 * <p>Thin pass-through wrapper. {@code keepAlive()} on the underlying client
 * is the caller's responsibility (typically invoked once when the SSE handler
 * opens the stream).</p>
 */
public class JavalinSseSink implements SseSink {

    private final SseClient client;

    /**
     * Wraps the given Javalin client.
     *
     * @param client the client to delegate to
     */
    public JavalinSseSink(SseClient client) {
        this.client = client;
    }

    @Override
    public void sendEvent(String eventName, Object data) {
        client.sendEvent(eventName, data);
    }

    @Override
    public boolean terminated() {
        return client.terminated();
    }

    @Override
    public void onClose(Runnable callback) {
        client.onClose(callback::run);
    }
}
