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

/** Tests for Continue Run (incremental execution) and Reset. */
public class SessionContinueRunTest {

    // --- Continue Run: second run starts from first run's endpoint ---

    @Test
    public void run_twice_secondRunContinuesFromFirstEndpoint() throws Exception {
        Session s = session();
        s.configure(bounded10x10("R1", 0, 0, "N", "MM"));

        // First run: R1 at (0,0)N → MM → (0,2)N
        awaitDone(s.run());
        SessionSnapshot snap1 = s.getSnapshot();
        assertEquals(0, snap1.rovers().get("R1").x());
        assertEquals(2, snap1.rovers().get("R1").y());
        assertEquals("NORTH", snap1.rovers().get("R1").direction());

        // Second run with new commands: continue from (0,2)N → RM → turn east, move to (1,2)E
        awaitDone(s.run(Map.of("R1", "RM")));
        SessionSnapshot snap2 = s.getSnapshot();
        assertEquals(1, snap2.rovers().get("R1").x());
        assertEquals(2, snap2.rovers().get("R1").y());
        assertEquals("EAST", snap2.rovers().get("R1").direction());
    }

    // --- Trails accumulate across runs ---

    @Test
    public void run_twice_trailsAccumulate() throws Exception {
        Session s = session();
        s.configure(bounded10x10("R1", 0, 0, "N", "MM"));

        awaitDone(s.run());
        int trailAfterFirst = s.getTrails().get("R1").size();
        assertTrue("trail should have positions after first run", trailAfterFirst >= 3);

        awaitDone(s.run(Map.of("R1", "RM")));
        int trailAfterSecond = s.getTrails().get("R1").size();
        assertTrue("trail should grow after second run", trailAfterSecond > trailAfterFirst);
    }

    // --- Stats accumulate across runs ---

    @Test
    public void run_twice_statsAccumulate() throws Exception {
        Session s = session();
        s.configure(bounded10x10("R1", 0, 0, "N", "MM"));

        awaitDone(s.run());
        assertEquals(2, s.getStats().totalSteps());

        awaitDone(s.run(Map.of("R1", "RM")));
        assertEquals(4, s.getStats().totalSteps()); // 2 + 2 = 4
    }

    // --- Run count increments ---

    @Test
    public void run_twice_runCountIncrements() throws Exception {
        Session s = session();
        s.configure(bounded10x10("R1", 0, 0, "N", "M"));

        assertEquals(0, s.getRunCount());
        awaitDone(s.run());
        assertEquals(1, s.getRunCount());
        awaitDone(s.run(Map.of("R1", "M")));
        assertEquals(2, s.getRunCount());
    }

    // --- resetToStart returns rovers to starting positions ---

    @Test
    public void resetToStart_returnsRoversToStartingPositions() throws Exception {
        Session s = session();
        s.configure(bounded10x10("R1", 0, 0, "N", "MMM"));

        awaitDone(s.run());
        assertEquals(3, s.getSnapshot().rovers().get("R1").y());

        s.resetToStart();
        assertEquals(0, s.getSnapshot().rovers().get("R1").x());
        assertEquals(0, s.getSnapshot().rovers().get("R1").y());
        assertEquals("NORTH", s.getSnapshot().rovers().get("R1").direction());
    }

    // --- resetToStart clears trails and stats ---

    @Test
    public void resetToStart_clearsTrailsAndStats() throws Exception {
        Session s = session();
        s.configure(bounded10x10("R1", 0, 0, "N", "MM"));

        awaitDone(s.run());
        assertTrue(s.getTrails().get("R1").size() > 0);
        assertTrue(s.getStats().totalSteps() > 0);

        s.resetToStart();
        assertEquals(0, s.getStats().totalSteps());
        assertEquals(0, s.getRunCount());
        assertTrue(s.getTrails().get("R1").isEmpty());
    }

    // --- resetToStart then run works from starting position ---

    @Test
    public void resetToStart_thenRun_executesFromStartingPosition() throws Exception {
        Session s = session();
        s.configure(bounded10x10("R1", 0, 0, "N", "MMM"));

        awaitDone(s.run());
        assertEquals(3, s.getSnapshot().rovers().get("R1").y());

        s.resetToStart();
        awaitDone(s.run(Map.of("R1", "M")));
        // Should be at (0,1) — ran from start, not from (0,3)
        assertEquals(0, s.getSnapshot().rovers().get("R1").x());
        assertEquals(1, s.getSnapshot().rovers().get("R1").y());
    }

    // --- resetToStart without configure throws ---

    @Test(expected = IllegalStateException.class)
    public void resetToStart_withoutConfigure_throws() {
        Session s = session();
        s.resetToStart();
    }

    // --- Multi-rover continue run ---

    @Test
    public void run_twice_multipleRovers_eachContinuesFromOwnEndpoint() throws Exception {
        Session s = session();
        s.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(
                        new RoverSpecDto("R1", 0, 0, "N", "MM"),
                        new RoverSpecDto("R2", 5, 5, "E", "MM")
                ), false));

        awaitDone(s.run());
        assertEquals(2, s.getSnapshot().rovers().get("R1").y());
        assertEquals(7, s.getSnapshot().rovers().get("R2").x());

        awaitDone(s.run(Map.of("R1", "R", "R2", "L")));
        assertEquals("EAST", s.getSnapshot().rovers().get("R1").direction());
        assertEquals("NORTH", s.getSnapshot().rovers().get("R2").direction());
    }

    // --- Helpers ---

    private static Session session() {
        return new Session("test", Clock.systemUTC());
    }

    private static ArenaConfig bounded10x10(String id, int x, int y, String dir, String cmds) {
        return new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto(id, x, y, dir, cmds)), false);
    }

    private static void awaitDone(Future<?> future) throws InterruptedException, ExecutionException {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("run did not complete in time");
        }
    }
}
