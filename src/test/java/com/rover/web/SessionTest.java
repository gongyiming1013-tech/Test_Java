package com.rover.web;

import com.rover.Position;
import org.junit.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

/** Contract tests for {@link Session}. */
public class SessionTest {

    // --- construction + id ---

    @Test
    public void newSession_hasIdAndInitialEmptyState() {
        Session s = newSession("abc-123");
        assertEquals("abc-123", s.getId());
        assertNull("no config before configure()", s.getConfig());
        assertNull("no arena before configure()", s.getArena());
        assertFalse("not running initially", s.isRunning());
        assertEquals("empty stats initially", 0, s.getStats().totalSteps());
    }

    // --- configure ---

    @Test
    public void configure_validConfig_buildsArenaAndStoresConfig() {
        Session s = newSession();
        ArenaConfig config = simpleConfig(10, 10);
        s.configure(config);
        assertNotNull(s.getArena());
        assertEquals(config, s.getConfig());
    }

    @Test(expected = ConfigValidationException.class)
    public void configure_invalidConfig_throws() {
        Session s = newSession();
        ArenaConfig bad = new ArenaConfig(10, null, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "")), false);
        s.configure(bad);
    }

    @Test
    public void configure_replacesExistingArena() {
        Session s = newSession();
        s.configure(simpleConfig(5, 5));
        s.configure(simpleConfig(10, 10));
        assertNotNull(s.getArena());
        assertEquals(Integer.valueOf(10), s.getConfig().width());
    }

    @Test
    public void configure_resetsTrailsAndStats() {
        Session s = newSession();
        s.configure(simpleConfig(10, 10));
        s.configure(simpleConfig(5, 5));
        assertEquals(0, s.getStats().totalSteps());
        Map<String, List<Position>> trails = s.getTrails();
        assertNotNull(trails);
        // All trails empty after fresh configure
        for (List<Position> trail : trails.values()) {
            assertTrue("trail should be empty after configure", trail.isEmpty());
        }
    }

    // --- run ---

    @Test
    public void run_withoutConfigure_throws() {
        Session s = newSession();
        try {
            s.run();
            fail("should have thrown");
        } catch (IllegalStateException | UnsupportedOperationException e) {
            // Either is acceptable until fully implemented
        }
    }

    @Test
    public void run_executesCommandsAndPopulatesTrails() throws Exception {
        Session s = newSession();
        s.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMM")), false));
        Future<?> future = s.run();
        awaitDone(future);

        Map<String, List<Position>> trails = s.getTrails();
        List<Position> r1Trail = trails.get("R1");
        assertNotNull(r1Trail);
        assertTrue("R1 should have visited multiple positions", r1Trail.size() >= 3);
    }

    @Test
    public void run_populatesStats() throws Exception {
        Session s = newSession();
        s.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMR")), false));
        Future<?> future = s.run();
        awaitDone(future);

        RunStats stats = s.getStats();
        assertEquals("3 actions executed", 3, stats.totalSteps());
        assertEquals("no blocks", 0, stats.blockedCount());
        assertEquals("1 rover", 1, stats.roverCount());
        assertTrue("duration should be non-negative", stats.durationMs() >= 0);
    }

    @Test
    public void run_notRunningAfterCompletion() throws Exception {
        Session s = newSession();
        s.configure(simpleConfig(10, 10));
        Future<?> future = s.run();
        awaitDone(future);
        assertFalse(s.isRunning());
    }

    // --- getSnapshot ---

    @Test
    public void getSnapshot_returnsFullState() throws Exception {
        Session s = newSession("sess-1");
        s.configure(new ArenaConfig(10, 10, false, List.of(new PositionDto(3, 3)), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMM")), false));
        awaitDone(s.run());

        SessionSnapshot snap = s.getSnapshot();
        assertEquals("sess-1", snap.sessionId());
        assertNotNull(snap.config());
        assertNotNull(snap.rovers());
        assertTrue(snap.rovers().containsKey("R1"));
        assertNotNull(snap.trails());
        assertNotNull(snap.viewport());
        assertNotNull(snap.stats());
        assertFalse(snap.running());
    }

    @Test
    public void getSnapshot_boundedConfig_viewportMatchesGrid() {
        Session s = newSession();
        s.configure(simpleConfig(10, 15));
        SessionSnapshot snap = s.getSnapshot();
        ViewportDto vp = snap.viewport();
        assertEquals(0, vp.xMin());
        assertEquals(0, vp.yMin());
        assertEquals(9, vp.xMax());
        assertEquals(14, vp.yMax());
    }

    @Test
    public void getSnapshot_unboundedConfig_viewportAutoFit() {
        Session s = newSession();
        s.configure(new ArenaConfig(null, null, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "")), false));
        SessionSnapshot snap = s.getSnapshot();
        ViewportDto vp = snap.viewport();
        // Should be at least 10x10 containing origin
        assertTrue(vp.width() >= 10);
        assertTrue(vp.height() >= 10);
        assertTrue(vp.xMin() <= 0 && vp.xMax() >= 0);
    }

    // --- touch / lastAccess ---

    @Test
    public void touch_updatesLastAccess() throws InterruptedException {
        Session s = newSession();
        long before = s.getLastAccessMillis();
        Thread.sleep(5);
        s.touch();
        long after = s.getLastAccessMillis();
        assertTrue("lastAccess should increase after touch", after > before);
    }

    // --- helpers ---

    private static Session newSession() {
        return new Session("test-session", Clock.systemUTC());
    }

    private static Session newSession(String id) {
        return new Session(id, Clock.systemUTC());
    }

    private static ArenaConfig simpleConfig(int w, int h) {
        return new ArenaConfig(w, h, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "")), false);
    }

    private static void awaitDone(Future<?> future) throws InterruptedException, ExecutionException {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("run did not complete in time");
        }
    }
}
