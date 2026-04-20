package com.rover.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * End-to-end tests for the V6b SSE flow against a real {@link WebApp}.
 *
 * <p>SSE consumption uses a raw {@link HttpURLConnection} so we can read
 * {@code text/event-stream} line-by-line without a third-party EventSource
 * library.</p>
 */
public class WebE2ESseTest {

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

    @Test
    public void sseEndpoint_unknownSession_returns404() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(
                baseUrl + "/api/session/nonexistent/events").toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "text/event-stream");
        int code = conn.getResponseCode();
        conn.disconnect();
        assertEquals(404, code);
    }

    @Test
    public void subscribe_firstEventIsStateSnapshot() throws Exception {
        String sid = createSession();
        configure(sid, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMR")), false, 0L));

        SseReader reader = SseReader.connect(baseUrl + "/api/session/" + sid + "/events");
        SseReader.Event first = reader.nextEvent(2000);
        reader.close();

        assertNotNull("expected a state event before close", first);
        assertEquals("state", first.name);
    }

    @Test
    public void subscribe_thenRun_receivesStepThenCompleteEvents() throws Exception {
        String sid = createSession();
        configure(sid, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMR")), false, 0L));

        SseReader reader = SseReader.connect(baseUrl + "/api/session/" + sid + "/events");
        // Drain initial state event
        reader.nextEvent(2000);

        // Trigger run
        post("/api/session/" + sid + "/run", "");

        List<SseReader.Event> stepEvents = new ArrayList<>();
        SseReader.Event completeEvent = null;
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            SseReader.Event ev = reader.nextEvent(500);
            if (ev == null) continue;
            if ("step".equals(ev.name)) stepEvents.add(ev);
            else if ("complete".equals(ev.name)) { completeEvent = ev; break; }
        }
        reader.close();

        assertEquals("3 step events expected", 3, stepEvents.size());
        assertNotNull("complete event expected", completeEvent);
    }

    @Test
    public void runWithDelayOverride_acceptsAndExecutes() throws Exception {
        String sid = createSession();
        configure(sid, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MM")), false, 1000L));

        // Override delay = 0 (Skip animation)
        HttpResponse<String> resp = post("/api/session/" + sid + "/run",
                "{\"delayMs\": 0}");
        assertEquals(202, resp.statusCode());

        // Should complete quickly thanks to delay=0 override
        Thread.sleep(300);
        HttpResponse<String> stateResp = http(URI.create(baseUrl + "/api/session/" + sid + "/state"), "GET", null);
        JsonNode snap = mapper.readTree(stateResp.body());
        assertFalse("run should have completed", snap.get("running").asBoolean());
    }

    @Test
    public void runWithInvalidDelayOverride_returns400() throws Exception {
        String sid = createSession();
        configure(sid, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "M")), false, 0L));

        HttpResponse<String> resp = post("/api/session/" + sid + "/run",
                "{\"delayMs\": -5}");
        assertEquals(400, resp.statusCode());
        JsonNode err = mapper.readTree(resp.body());
        assertEquals("INVALID_DELAY", err.get("code").asText());
    }

    @Test
    public void configWithDelayMs_persisted() throws Exception {
        String sid = createSession();
        configure(sid, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "")), false, 250L));

        HttpResponse<String> resp = http(URI.create(baseUrl + "/api/session/" + sid + "/state"), "GET", null);
        JsonNode snap = mapper.readTree(resp.body());
        assertEquals(250, snap.get("config").get("delayMs").asLong());
    }

    @Test
    public void multipleSubscribers_allReceiveSameEvents() throws Exception {
        String sid = createSession();
        configure(sid, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MM")), false, 0L));

        SseReader r1 = SseReader.connect(baseUrl + "/api/session/" + sid + "/events");
        SseReader r2 = SseReader.connect(baseUrl + "/api/session/" + sid + "/events");
        // drain initial state events
        r1.nextEvent(2000);
        r2.nextEvent(2000);

        post("/api/session/" + sid + "/run", "");

        int r1Steps = 0, r2Steps = 0;
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline && (r1Steps < 2 || r2Steps < 2)) {
            SseReader.Event a = r1.nextEvent(200);
            SseReader.Event b = r2.nextEvent(200);
            if (a != null && "step".equals(a.name)) r1Steps++;
            if (b != null && "step".equals(b.name)) r2Steps++;
        }
        r1.close();
        r2.close();

        assertEquals(2, r1Steps);
        assertEquals(2, r2Steps);
    }

    @Test
    public void v6aBackwardCompat_runWithoutDelayOverride_stillWorks() throws Exception {
        // Body with no delayMs — must still hit existing V6a path
        String sid = createSession();
        configure(sid, new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MM")), false));

        HttpResponse<String> resp = post("/api/session/" + sid + "/run", "");
        assertEquals(202, resp.statusCode());
    }

    // ----------------- helpers -----------------

    private String createSession() throws Exception {
        HttpResponse<String> resp = post("/api/session", "");
        return mapper.readTree(resp.body()).get("sessionId").asText();
    }

    private void configure(String sid, ArenaConfig config) throws Exception {
        HttpResponse<String> resp = http(URI.create(baseUrl + "/api/session/" + sid + "/config"),
                "PUT", mapper.writeValueAsString(config));
        if (resp.statusCode() != 200) fail("configure: " + resp.statusCode() + " " + resp.body());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http(URI.create(baseUrl + path), "POST", body);
    }

    private HttpResponse<String> http(URI uri, String method, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(uri)
                .header("Content-Type", "application/json");
        switch (method) {
            case "GET" -> b.GET();
            case "POST" -> b.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
            case "PUT" -> b.PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int pickFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
    }

    /** Minimal SSE line-protocol reader for tests. */
    static class SseReader {
        private final HttpURLConnection conn;
        private final BufferedReader reader;

        private SseReader(HttpURLConnection conn, BufferedReader reader) {
            this.conn = conn;
            this.reader = reader;
        }

        static SseReader connect(String url) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setReadTimeout(0);
            conn.connect();
            int code = conn.getResponseCode();
            if (code != 200) {
                conn.disconnect();
                throw new IOException("SSE connect failed: " + code);
            }
            InputStream in = conn.getInputStream();
            return new SseReader(conn, new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }

        /** Reads the next complete event, or null on timeout. */
        Event nextEvent(long timeoutMs) throws IOException {
            conn.setReadTimeout((int) Math.max(timeoutMs, 1));
            String name = "message";
            StringBuilder data = new StringBuilder();
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (data.length() > 0) return new Event(name, data.toString());
                        continue;
                    }
                    if (line.startsWith("event:")) name = line.substring(6).trim();
                    else if (line.startsWith("data:")) {
                        if (data.length() > 0) data.append("\n");
                        data.append(line.substring(5).trim());
                    }
                }
            } catch (java.net.SocketTimeoutException e) {
                return null;
            }
            return null;
        }

        void close() {
            try { reader.close(); } catch (IOException ignored) {}
            conn.disconnect();
        }

        static class Event {
            final String name;
            final String data;
            Event(String name, String data) { this.name = name; this.data = data; }
        }
    }

    private CountDownLatch unused;
}
