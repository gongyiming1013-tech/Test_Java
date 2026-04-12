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

/**
 * End-to-end tests that start a real {@link WebApp}, send HTTP requests via
 * {@link HttpClient}, and verify responses. These cover the REST surface
 * holistically (routing, JSON marshalling, error responses, session lifecycle).
 */
public class WebE2ETest {

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
        if (app != null && app.isRunning()) {
            app.stop();
        }
    }

    // --- POST /api/session ---

    @Test
    public void createSession_returns200WithSessionId() throws Exception {
        HttpResponse<String> resp = post("/api/session", "");
        assertEquals(200, resp.statusCode());
        JsonNode body = mapper.readTree(resp.body());
        assertTrue(body.has("sessionId"));
        assertFalse(body.get("sessionId").asText().isEmpty());
    }

    @Test
    public void createSession_multipleCalls_distinctIds() throws Exception {
        String id1 = mapper.readTree(post("/api/session", "").body()).get("sessionId").asText();
        String id2 = mapper.readTree(post("/api/session", "").body()).get("sessionId").asText();
        assertNotEquals(id1, id2);
    }

    // --- PUT /api/session/{id}/config ---

    @Test
    public void configure_validConfig_returns200() throws Exception {
        String sessionId = createSession();
        ArenaConfig config = new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMR")), false);

        HttpResponse<String> resp = put("/api/session/" + sessionId + "/config",
                mapper.writeValueAsString(config));
        assertEquals(200, resp.statusCode());
    }

    @Test
    public void configure_unknownSession_returns404() throws Exception {
        ArenaConfig config = new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "M")), false);

        HttpResponse<String> resp = put("/api/session/nonexistent/config",
                mapper.writeValueAsString(config));
        assertEquals(404, resp.statusCode());
        JsonNode err = mapper.readTree(resp.body());
        assertTrue(err.has("code"));
        assertTrue(err.has("message"));
    }

    @Test
    public void configure_invalidGrid_returns400WithInvalidGridCode() throws Exception {
        String sessionId = createSession();
        // width set, height null → invalid
        ArenaConfig bad = new ArenaConfig(10, null, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "M")), false);

        HttpResponse<String> resp = put("/api/session/" + sessionId + "/config",
                mapper.writeValueAsString(bad));
        assertEquals(400, resp.statusCode());
        JsonNode err = mapper.readTree(resp.body());
        assertEquals("INVALID_GRID", err.get("code").asText());
    }

    @Test
    public void configure_invalidDirection_returns400() throws Exception {
        String sessionId = createSession();
        ArenaConfig bad = new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "X", "M")), false);

        HttpResponse<String> resp = put("/api/session/" + sessionId + "/config",
                mapper.writeValueAsString(bad));
        assertEquals(400, resp.statusCode());
    }

    // --- POST /api/session/{id}/run ---

    @Test
    public void run_configured_returns202() throws Exception {
        String sessionId = createSession();
        configure(sessionId, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMR")), false));

        HttpResponse<String> resp = post("/api/session/" + sessionId + "/run", "");
        assertEquals(202, resp.statusCode());
    }

    @Test
    public void run_notConfigured_returns409() throws Exception {
        String sessionId = createSession();
        HttpResponse<String> resp = post("/api/session/" + sessionId + "/run", "");
        assertEquals(409, resp.statusCode());
    }

    @Test
    public void run_unknownSession_returns404() throws Exception {
        HttpResponse<String> resp = post("/api/session/nonexistent/run", "");
        assertEquals(404, resp.statusCode());
    }

    // --- GET /api/session/{id}/state ---

    @Test
    public void getState_afterRun_returnsFinalPosition() throws Exception {
        String sessionId = createSession();
        configure(sessionId, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMM")), false));
        post("/api/session/" + sessionId + "/run", "");

        // Give the async run a moment to complete
        Thread.sleep(200);

        HttpResponse<String> resp = get("/api/session/" + sessionId + "/state");
        assertEquals(200, resp.statusCode());

        JsonNode snap = mapper.readTree(resp.body());
        assertEquals(sessionId, snap.get("sessionId").asText());
        assertTrue(snap.has("config"));
        assertTrue(snap.has("rovers"));
        assertTrue(snap.has("trails"));
        assertTrue(snap.has("viewport"));
        assertTrue(snap.has("stats"));
        assertFalse(snap.get("running").asBoolean());

        // R1 should be at (0, 3) facing NORTH after MMM
        JsonNode r1 = snap.get("rovers").get("R1");
        assertEquals(0, r1.get("x").asInt());
        assertEquals(3, r1.get("y").asInt());
        assertEquals("NORTH", r1.get("direction").asText());
    }

    @Test
    public void getState_boundedConfig_viewportMatchesGrid() throws Exception {
        String sessionId = createSession();
        configure(sessionId, new ArenaConfig(10, 15, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "")), false));

        HttpResponse<String> resp = get("/api/session/" + sessionId + "/state");
        JsonNode vp = mapper.readTree(resp.body()).get("viewport");
        assertEquals(0, vp.get("xMin").asInt());
        assertEquals(0, vp.get("yMin").asInt());
        assertEquals(9, vp.get("xMax").asInt());
        assertEquals(14, vp.get("yMax").asInt());
    }

    @Test
    public void getState_unboundedConfig_viewportAutoFit() throws Exception {
        String sessionId = createSession();
        configure(sessionId, new ArenaConfig(null, null, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "")), false));

        HttpResponse<String> resp = get("/api/session/" + sessionId + "/state");
        JsonNode vp = mapper.readTree(resp.body()).get("viewport");
        // Should be at least 10x10 including origin
        int width = vp.get("xMax").asInt() - vp.get("xMin").asInt() + 1;
        int height = vp.get("yMax").asInt() - vp.get("yMin").asInt() + 1;
        assertTrue(width >= 10);
        assertTrue(height >= 10);
        assertTrue(vp.get("xMin").asInt() <= 0);
        assertTrue(vp.get("yMin").asInt() <= 0);
    }

    @Test
    public void getState_unknownSession_returns404() throws Exception {
        HttpResponse<String> resp = get("/api/session/nonexistent/state");
        assertEquals(404, resp.statusCode());
    }

    // --- DELETE /api/session/{id} ---

    @Test
    public void deleteSession_existing_returns204() throws Exception {
        String sessionId = createSession();
        HttpResponse<String> resp = delete("/api/session/" + sessionId);
        assertEquals(204, resp.statusCode());
    }

    @Test
    public void deleteSession_subsequentGetReturns404() throws Exception {
        String sessionId = createSession();
        delete("/api/session/" + sessionId);
        HttpResponse<String> resp = get("/api/session/" + sessionId + "/state");
        assertEquals(404, resp.statusCode());
    }

    @Test
    public void deleteSession_unknownSession_returns404() throws Exception {
        HttpResponse<String> resp = delete("/api/session/nonexistent");
        assertEquals(404, resp.statusCode());
    }

    // --- Full lifecycle ---

    @Test
    public void fullFlow_createConfigureRunStateDelete() throws Exception {
        String sessionId = createSession();

        configure(sessionId, new ArenaConfig(5, 5, false,
                List.of(new PositionDto(2, 2)), "skip",
                List.of(
                        new RoverSpecDto("R1", 0, 0, "N", "MM"),
                        new RoverSpecDto("R2", 4, 4, "S", "MM")
                ),
                false));

        post("/api/session/" + sessionId + "/run", "");
        Thread.sleep(200);

        HttpResponse<String> stateResp = get("/api/session/" + sessionId + "/state");
        assertEquals(200, stateResp.statusCode());

        HttpResponse<String> delResp = delete("/api/session/" + sessionId);
        assertEquals(204, delResp.statusCode());
    }

    // --- HTTP helpers ---

    private String createSession() throws Exception {
        HttpResponse<String> resp = post("/api/session", "");
        return mapper.readTree(resp.body()).get("sessionId").asText();
    }

    private void configure(String sessionId, ArenaConfig config) throws Exception {
        HttpResponse<String> resp = put("/api/session/" + sessionId + "/config",
                mapper.writeValueAsString(config));
        if (resp.statusCode() != 200) {
            fail("configure failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .DELETE()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int pickFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
