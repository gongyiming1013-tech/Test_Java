package com.rover.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** E2E tests for Continue Run and Reset endpoints. */
public class WebE2EContinueRunTest {

    private WebApp app;
    private HttpClient http;
    private ObjectMapper mapper;
    private String baseUrl;

    @Before
    public void setUp() throws IOException {
        int port = pickFreePort();
        app = new WebApp(port);
        app.start();
        baseUrl = "http://127.0.0.1:" + port;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        mapper = new ObjectMapper();
    }

    @After
    public void tearDown() {
        if (app != null && app.isRunning()) app.stop();
    }

    // --- Continue Run: two runs chain positions ---

    @Test
    public void continueRun_secondRunContinuesFromFirstEndpoint() throws Exception {
        String sid = createSession();
        configure(sid, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MM")), false, 0L));

        // First run
        runWithCommands(sid, Map.of("R1", "MM"));
        Thread.sleep(200);
        JsonNode snap1 = getState(sid);
        assertEquals(0, snap1.get("rovers").get("R1").get("x").asInt());
        assertEquals(2, snap1.get("rovers").get("R1").get("y").asInt());

        // Second run — continues from (0,2)N
        runWithCommands(sid, Map.of("R1", "RM"));
        Thread.sleep(200);
        JsonNode snap2 = getState(sid);
        assertEquals(1, snap2.get("rovers").get("R1").get("x").asInt());
        assertEquals(2, snap2.get("rovers").get("R1").get("y").asInt());
        assertEquals("EAST", snap2.get("rovers").get("R1").get("direction").asText());
    }

    // --- Stats accumulate ---

    @Test
    public void continueRun_statsAccumulateAcrossRuns() throws Exception {
        String sid = createSession();
        configure(sid, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MM")), false, 0L));

        runWithCommands(sid, Map.of("R1", "MM"));
        Thread.sleep(200);
        JsonNode snap1 = getState(sid);
        assertEquals(2, snap1.get("stats").get("totalSteps").asInt());

        runWithCommands(sid, Map.of("R1", "MR"));
        Thread.sleep(200);
        JsonNode snap2 = getState(sid);
        assertEquals(4, snap2.get("stats").get("totalSteps").asInt());
    }

    // --- Reset endpoint ---

    @Test
    public void reset_returnsRoversToStartingPositions() throws Exception {
        String sid = createSession();
        configure(sid, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMM")), false, 0L));

        runWithCommands(sid, Map.of("R1", "MMM"));
        Thread.sleep(200);
        JsonNode snap = getState(sid);
        assertEquals(3, snap.get("rovers").get("R1").get("y").asInt());

        // Reset
        HttpResponse<String> resetResp = post("/api/session/" + sid + "/reset", "");
        assertEquals(200, resetResp.statusCode());

        JsonNode afterReset = getState(sid);
        assertEquals(0, afterReset.get("rovers").get("R1").get("x").asInt());
        assertEquals(0, afterReset.get("rovers").get("R1").get("y").asInt());
        assertEquals(0, afterReset.get("stats").get("totalSteps").asInt());
    }

    // --- Reset then Run starts from beginning ---

    @Test
    public void reset_thenRun_executesFromStartingPosition() throws Exception {
        String sid = createSession();
        configure(sid, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMM")), false, 0L));

        runWithCommands(sid, Map.of("R1", "MMM"));
        Thread.sleep(200);

        post("/api/session/" + sid + "/reset", "");

        runWithCommands(sid, Map.of("R1", "M"));
        Thread.sleep(200);
        JsonNode snap = getState(sid);
        assertEquals(0, snap.get("rovers").get("R1").get("x").asInt());
        assertEquals(1, snap.get("rovers").get("R1").get("y").asInt());
    }

    // --- Reset on unconfigured session ---

    @Test
    public void reset_unconfiguredSession_returns409() throws Exception {
        String sid = createSession();
        HttpResponse<String> resp = post("/api/session/" + sid + "/reset", "");
        assertEquals(409, resp.statusCode());
    }

    // --- Reset on unknown session ---

    @Test
    public void reset_unknownSession_returns404() throws Exception {
        HttpResponse<String> resp = post("/api/session/nonexistent/reset", "");
        assertEquals(404, resp.statusCode());
    }

    // --- Helpers ---

    private String createSession() throws Exception {
        HttpResponse<String> resp = post("/api/session", "");
        return mapper.readTree(resp.body()).get("sessionId").asText();
    }

    private void configure(String sid, ArenaConfig config) throws Exception {
        HttpResponse<String> resp = put("/api/session/" + sid + "/config", mapper.writeValueAsString(config));
        assertEquals(200, resp.statusCode());
    }

    private void runWithCommands(String sid, Map<String, String> commands) throws Exception {
        String body = mapper.writeValueAsString(Map.of("commands", commands));
        HttpResponse<String> resp = post("/api/session/" + sid + "/run", body);
        assertEquals(202, resp.statusCode());
    }

    private JsonNode getState(String sid) throws Exception {
        HttpResponse<String> resp = get("/api/session/" + sid + "/state");
        assertEquals(200, resp.statusCode());
        return mapper.readTree(resp.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int pickFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
    }
}
