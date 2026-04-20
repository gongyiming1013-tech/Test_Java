package com.rover.web;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;

import static org.junit.Assert.*;

/** Contract tests for {@link Session} subscribe/unsubscribe/broadcast (V6b). */
public class SessionSubscriptionTest {

    private Session session;

    @Before
    public void setUp() {
        session = new Session("test", Clock.systemUTC());
    }

    @Test
    public void freshSession_hasNoSubscribers() {
        assertEquals(0, session.getSubscriberCount());
    }

    @Test
    public void subscribe_addsSink() {
        RecordingSseSink sink = new RecordingSseSink();
        session.subscribe(sink);
        assertEquals(1, session.getSubscriberCount());
    }

    @Test
    public void subscribe_sameTwice_idempotent() {
        RecordingSseSink sink = new RecordingSseSink();
        session.subscribe(sink);
        session.subscribe(sink);
        assertEquals("re-subscribing same sink must not duplicate", 1, session.getSubscriberCount());
    }

    @Test
    public void unsubscribe_removesSink_andReturnsTrue() {
        RecordingSseSink sink = new RecordingSseSink();
        session.subscribe(sink);
        boolean removed = session.unsubscribe(sink);
        assertTrue(removed);
        assertEquals(0, session.getSubscriberCount());
    }

    @Test
    public void unsubscribe_unknown_returnsFalse() {
        RecordingSseSink sink = new RecordingSseSink();
        assertFalse(session.unsubscribe(sink));
    }

    @Test
    public void broadcast_reachesAllLiveSubscribers_inOrder() {
        RecordingSseSink a = new RecordingSseSink();
        RecordingSseSink b = new RecordingSseSink();
        RecordingSseSink c = new RecordingSseSink();
        session.subscribe(a);
        session.subscribe(b);
        session.subscribe(c);

        session.broadcast("step", "payload-1");
        session.broadcast("step", "payload-2");

        for (RecordingSseSink sink : new RecordingSseSink[]{a, b, c}) {
            assertEquals(2, sink.events.size());
            assertEquals("payload-1", sink.events.get(0).payload);
            assertEquals("payload-2", sink.events.get(1).payload);
        }
    }

    @Test
    public void broadcast_terminatedSink_autoRemoved() {
        RecordingSseSink live = new RecordingSseSink();
        RecordingSseSink dead = new RecordingSseSink();
        session.subscribe(live);
        session.subscribe(dead);
        dead.simulateClose();

        session.broadcast("step", "payload");

        assertEquals("live sink received event", 1, live.events.size());
        assertEquals("dead sink not invoked", 0, dead.events.size());
        assertEquals("dead sink removed from registry", 1, session.getSubscriberCount());
    }

    @Test
    public void broadcast_sinkThatThrows_autoRemoved() {
        RecordingSseSink live = new RecordingSseSink();
        RecordingSseSink failing = new RecordingSseSink();
        session.subscribe(live);
        session.subscribe(failing);
        failing.simulateSendFailure();

        session.broadcast("step", "payload");

        assertEquals(1, live.events.size());
        assertEquals("failing sink removed from registry", 1, session.getSubscriberCount());
    }

    @Test
    public void broadcast_noSubscribers_silentNoop() {
        session.broadcast("step", "payload");
        // no exception
    }

    @Test
    public void broadcast_oneSlowSinkDoesNotPreventOthersFromReceiving() {
        RecordingSseSink failing = new RecordingSseSink();
        RecordingSseSink live = new RecordingSseSink();
        failing.simulateSendFailure();
        session.subscribe(failing);
        session.subscribe(live);

        session.broadcast("step", "payload");

        assertEquals(1, live.events.size());
    }
}
