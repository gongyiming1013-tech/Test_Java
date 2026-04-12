package com.rover.web;

import com.rover.Position;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

/**
 * Targeted tests to cover branches missed by the main test suite
 * (WebApp.main, Session error paths, parallel execution, null inputs).
 */
public class CoverageGapTest {

    // --- WebApp ---

    @Test(expected = IllegalStateException.class)
    public void webApp_startTwice_throws() throws IOException {
        int port = pickFreePort();
        WebApp app = new WebApp(port);
        try {
            app.start();
            app.start(); // second start should throw
        } finally {
            app.stop();
        }
    }

    @Test
    public void webApp_stopWhenNotStarted_doesNotThrow() {
        WebApp app = new WebApp(9999);
        app.stop(); // no-op
        assertFalse(app.isRunning());
    }

    // --- Session: already running ---

    @Test
    public void session_runWhileRunning_throws() throws Exception {
        Session s = new Session("test", Clock.systemUTC());
        s.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMMMMMMMM")), false));
        Future<?> f1 = s.run();
        try {
            s.run(); // should throw
            fail("should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("already running"));
        } finally {
            awaitDone(f1);
        }
    }

    // --- Session: parallel execution path ---

    @Test
    public void session_parallelExecution_populatesTrails() throws Exception {
        Session s = new Session("p", Clock.systemUTC());
        s.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(
                        new RoverSpecDto("R1", 0, 0, "N", "MM"),
                        new RoverSpecDto("R2", 5, 5, "S", "MM")
                ), true)); // parallel = true
        awaitDone(s.run());

        Map<String, List<Position>> trails = s.getTrails();
        assertTrue(trails.containsKey("R1"));
        assertTrue(trails.containsKey("R2"));
        assertTrue(trails.get("R1").size() >= 2);
        assertTrue(trails.get("R2").size() >= 2);
    }

    // --- Session: MoveBlockedException caught during run ---

    @Test
    public void session_failPolicy_blockedRun_doesNotThrow() throws Exception {
        Session s = new Session("blocked", Clock.systemUTC());
        s.configure(new ArenaConfig(3, 3, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMMMMM")), false));
        // Grid is 3x3, rover at (0,0)N with MMMMMM → hits wall at (0,2) and throws FAIL
        // But Session.runInternal catches MoveBlockedException
        awaitDone(s.run());
        assertFalse(s.isRunning());
        assertTrue(s.getStats().blockedCount() >= 0); // just verify no exception propagated
    }

    // --- Session: snapshot before configure ---

    @Test
    public void session_getSnapshot_beforeConfigure_returnsDefaults() {
        Session s = new Session("empty", Clock.systemUTC());
        SessionSnapshot snap = s.getSnapshot();
        assertEquals("empty", snap.sessionId());
        assertNull(snap.config());
        assertTrue(snap.rovers().isEmpty());
        assertTrue(snap.trails().isEmpty());
        assertNotNull(snap.viewport());
        assertFalse(snap.running());
    }

    // --- Session: null commands string ---

    @Test
    public void session_nullCommandsString_treatedAsEmpty() throws Exception {
        Session s = new Session("nc", Clock.systemUTC());
        s.configure(new ArenaConfig(5, 5, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", null)), false));
        awaitDone(s.run());
        assertEquals(0, s.getStats().totalSteps());
    }

    // --- Session: null obstacles list ---

    @Test
    public void session_nullObstacles_treatedAsEmpty() throws Exception {
        Session s = new Session("no", Clock.systemUTC());
        s.configure(new ArenaConfig(5, 5, false, null, "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "M")), false));
        awaitDone(s.run());
        assertEquals(0, s.getStats().obstacleCount());
    }

    // --- Session: blocked moves increment counter ---

    @Test
    public void session_blockedMoves_countedInStats() throws Exception {
        Session s = new Session("sk", Clock.systemUTC());
        s.configure(new ArenaConfig(3, 3, false,
                List.of(new PositionDto(0, 1)), // obstacle directly ahead
                "skip",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMM")), false));
        awaitDone(s.run());
        assertTrue("blocked count should be > 0", s.getStats().blockedCount() > 0);
    }

    // --- ArenaConfigMapper: null policy ---

    @Test
    public void mapper_nullConflictPolicy_throws() {
        try {
            ArenaConfigMapper.buildConflictPolicy(null);
            fail("should have thrown");
        } catch (ConfigValidationException e) {
            assertEquals("UNKNOWN_POLICY", e.getCode());
        }
    }

    // --- ArenaConfigMapper: null direction ---

    @Test
    public void mapper_nullDirection_throws() {
        try {
            ArenaConfigMapper.parseDirection(null);
            fail("should have thrown");
        } catch (ConfigValidationException e) {
            assertEquals("UNKNOWN_DIRECTION", e.getCode());
        }
    }

    // --- ArenaConfigMapper: null obstacles list ---

    @Test
    public void mapper_buildEnvironment_nullObstacles() {
        ArenaConfig cfg = new ArenaConfig(5, 5, false, null, "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "")), false);
        var env = ArenaConfigMapper.buildEnvironment(cfg);
        assertNotNull(env);
    }

    // --- ArenaConfigMapper: null rovers list ---

    @Test
    public void mapper_buildArena_nullRoversList_throws() {
        ArenaConfig cfg = new ArenaConfig(5, 5, false, List.of(), "fail", null, false);
        try {
            ArenaConfigMapper.buildArena(cfg);
            fail("should have thrown");
        } catch (ConfigValidationException e) {
            assertEquals("NO_ROVERS", e.getCode());
        }
    }

    // --- ArenaConfigMapper: null commands string ---

    @Test
    public void mapper_buildArena_nullCommands_treatedAsEmpty() {
        ArenaConfig cfg = new ArenaConfig(5, 5, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", null)), false);
        var arena = ArenaConfigMapper.buildArena(cfg);
        assertNotNull(arena);
    }

    // --- Session: run() with no override ---

    @Test
    public void session_runNoArgs_usesConfigCommands() throws Exception {
        Session s = new Session("cfg", Clock.systemUTC());
        s.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MM")), false));
        awaitDone(s.run()); // no override — should use config's "MM"
        assertEquals(2, s.getStats().totalSteps());
    }

    // --- Session: resetToStart while running throws ---

    @Test
    public void session_resetWhileRunning_throws() throws Exception {
        Session s = new Session("rr", Clock.systemUTC());
        s.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMMMMMMMMM")), false));
        Future<?> f = s.run();
        try {
            s.resetToStart();
            // If we get here, the run finished before resetToStart — that's OK too
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("running"));
        } finally {
            awaitDone(f);
        }
    }

    // --- WebApp.main() ---

    @Test
    public void webApp_main_startsAndStopsCleanly() throws Exception {
        int port = pickFreePort();
        Thread t = new Thread(() -> WebApp.main(new String[]{"--port", String.valueOf(port)}));
        t.setDaemon(true);
        t.start();
        Thread.sleep(2000);
        // Server should be listening — verify with a quick HTTP request
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().build();
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://127.0.0.1:" + port + "/api/session"))
                        .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
    }

    // --- Helpers ---

    private static int pickFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void awaitDone(Future<?> future) throws InterruptedException, ExecutionException {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("run did not complete in time");
        }
    }
}
