package com.rover.web;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe registry of {@link Session} instances keyed by session ID.
 *
 * <p>Provides session creation, lookup, explicit removal, and background
 * TTL-based eviction via {@link #reapExpired()}. Uses an injectable
 * {@link Clock} so tests can advance time deterministically.</p>
 */
public class SessionManager {

    /** Default idle TTL for sessions. */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final Clock clock;
    private final Duration ttl;
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Creates a session manager with the system clock and the default TTL.
     */
    public SessionManager() {
        this(Clock.systemUTC(), DEFAULT_TTL);
    }

    /**
     * Creates a session manager with a custom clock and TTL. Used by tests
     * to inject a fake clock and short TTL.
     *
     * @param clock clock for TTL calculations
     * @param ttl   idle duration after which a session is considered expired
     */
    public SessionManager(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    /**
     * Creates a new session with a freshly generated UUID and registers it.
     *
     * @return the new session
     */
    public Session createSession() {
        String id = UUID.randomUUID().toString();
        Session session = new Session(id, clock);
        sessions.put(id, session);
        return session;
    }

    /**
     * Retrieves a session by ID.
     *
     * @param id session ID
     * @return the session, or {@code null} if not found
     */
    public Session getSession(String id) {
        return sessions.get(id);
    }

    /**
     * Explicitly removes a session by ID.
     *
     * @param id session ID
     * @return {@code true} if a session was removed, {@code false} if not found
     */
    public boolean removeSession(String id) {
        return sessions.remove(id) != null;
    }

    /**
     * Returns the number of currently registered sessions.
     *
     * @return session count
     */
    public int size() {
        return sessions.size();
    }

    /**
     * Evicts all sessions whose last-access timestamp is older than the TTL.
     * Safe to call from any thread; typically invoked by a background reaper.
     *
     * @return number of sessions evicted
     */
    public int reapExpired() {
        long now = clock.millis();
        long ttlMillis = ttl.toMillis();
        int reaped = 0;
        Iterator<Session> it = sessions.values().iterator();
        while (it.hasNext()) {
            Session s = it.next();
            if (now - s.getLastAccessMillis() > ttlMillis) {
                it.remove();
                reaped++;
            }
        }
        return reaped;
    }

    /**
     * Stops the reaper thread (if running) and clears all sessions.
     */
    public void shutdown() {
        sessions.clear();
    }
}
