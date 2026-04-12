package com.rover.web;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Contract tests for {@link SessionManager}. */
public class SessionManagerTest {

    // --- createSession / getSession ---

    @Test
    public void createSession_returnsSessionWithNonNullId() {
        SessionManager mgr = new SessionManager();
        Session s = mgr.createSession();
        assertNotNull(s);
        assertNotNull(s.getId());
        assertFalse(s.getId().isEmpty());
    }

    @Test
    public void createSession_multipleCalls_returnDistinctIds() {
        SessionManager mgr = new SessionManager();
        Session a = mgr.createSession();
        Session b = mgr.createSession();
        Session c = mgr.createSession();
        assertNotEquals(a.getId(), b.getId());
        assertNotEquals(b.getId(), c.getId());
        assertNotEquals(a.getId(), c.getId());
    }

    @Test
    public void getSession_returnsRegisteredSession() {
        SessionManager mgr = new SessionManager();
        Session created = mgr.createSession();
        Session fetched = mgr.getSession(created.getId());
        assertSame(created, fetched);
    }

    @Test
    public void getSession_unknownId_returnsNull() {
        SessionManager mgr = new SessionManager();
        assertNull(mgr.getSession("nonexistent"));
    }

    // --- removeSession ---

    @Test
    public void removeSession_existing_returnsTrue() {
        SessionManager mgr = new SessionManager();
        Session s = mgr.createSession();
        assertTrue(mgr.removeSession(s.getId()));
        assertNull(mgr.getSession(s.getId()));
    }

    @Test
    public void removeSession_nonexistent_returnsFalse() {
        SessionManager mgr = new SessionManager();
        assertFalse(mgr.removeSession("nonexistent"));
    }

    // --- size ---

    @Test
    public void size_reflectsCreatedAndRemoved() {
        SessionManager mgr = new SessionManager();
        assertEquals(0, mgr.size());
        Session a = mgr.createSession();
        assertEquals(1, mgr.size());
        mgr.createSession();
        assertEquals(2, mgr.size());
        mgr.removeSession(a.getId());
        assertEquals(1, mgr.size());
    }

    // --- reapExpired with injected clock ---

    @Test
    public void reapExpired_noExpiredSessions_returnsZero() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.ofEpochMilli(0));
        Clock clock = stepClock(now);
        SessionManager mgr = new SessionManager(clock, Duration.ofMinutes(30));
        mgr.createSession();
        assertEquals(0, mgr.reapExpired());
        assertEquals(1, mgr.size());
    }

    @Test
    public void reapExpired_expiredSessionsRemoved() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.ofEpochMilli(0));
        Clock clock = stepClock(now);
        SessionManager mgr = new SessionManager(clock, Duration.ofMinutes(30));

        Session s = mgr.createSession();
        assertEquals(1, mgr.size());

        // Advance time beyond TTL
        now.set(Instant.ofEpochMilli(Duration.ofMinutes(31).toMillis()));

        int reaped = mgr.reapExpired();
        assertEquals(1, reaped);
        assertEquals(0, mgr.size());
        assertNull(mgr.getSession(s.getId()));
    }

    @Test
    public void reapExpired_touchExtendsLifetime() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.ofEpochMilli(0));
        Clock clock = stepClock(now);
        SessionManager mgr = new SessionManager(clock, Duration.ofMinutes(30));

        Session s = mgr.createSession();

        // Advance 20 minutes, touch
        now.set(Instant.ofEpochMilli(Duration.ofMinutes(20).toMillis()));
        s.touch();

        // Advance another 20 minutes (total 40 min, but last-access was 20 min ago)
        now.set(Instant.ofEpochMilli(Duration.ofMinutes(40).toMillis()));

        assertEquals("should not be expired (only 20 min since touch)", 0, mgr.reapExpired());
        assertEquals(1, mgr.size());
    }

    // --- shutdown ---

    @Test
    public void shutdown_clearsAllSessions() {
        SessionManager mgr = new SessionManager();
        mgr.createSession();
        mgr.createSession();
        mgr.shutdown();
        assertEquals(0, mgr.size());
    }

    // --- Test helper: a fixed, mutable clock ---

    private static Clock stepClock(AtomicReference<Instant> ref) {
        return new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return ref.get(); }
        };
    }
}
