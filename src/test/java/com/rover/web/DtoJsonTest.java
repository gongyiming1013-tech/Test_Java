package com.rover.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Verifies that all V6 DTOs round-trip correctly through Jackson.
 * Catches accidental non-serializable fields, missing getters, and
 * shape mismatches between Java records and expected JSON.
 */
public class DtoJsonTest {

    private ObjectMapper mapper;

    @Before
    public void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    public void positionDto_roundTrip() throws Exception {
        PositionDto original = new PositionDto(3, 7);
        String json = mapper.writeValueAsString(original);
        PositionDto back = mapper.readValue(json, PositionDto.class);
        assertEquals(original, back);
        assertTrue(json.contains("\"x\":3"));
        assertTrue(json.contains("\"y\":7"));
    }

    @Test
    public void roverSpecDto_roundTrip() throws Exception {
        RoverSpecDto original = new RoverSpecDto("R1", 2, 3, "N", "MMRMM");
        String json = mapper.writeValueAsString(original);
        RoverSpecDto back = mapper.readValue(json, RoverSpecDto.class);
        assertEquals(original, back);
    }

    @Test
    public void roverStateDto_roundTrip() throws Exception {
        RoverStateDto original = new RoverStateDto(5, 6, "NORTH");
        String json = mapper.writeValueAsString(original);
        RoverStateDto back = mapper.readValue(json, RoverStateDto.class);
        assertEquals(original, back);
    }

    @Test
    public void viewportDto_roundTrip() throws Exception {
        ViewportDto original = new ViewportDto(-5, -3, 10, 12);
        String json = mapper.writeValueAsString(original);
        ViewportDto back = mapper.readValue(json, ViewportDto.class);
        assertEquals(original, back);
    }

    @Test
    public void viewportDto_widthHeight_computed() {
        ViewportDto vp = new ViewportDto(0, 0, 9, 14);
        assertEquals(10, vp.width());
        assertEquals(15, vp.height());
    }

    @Test
    public void runStats_roundTrip() throws Exception {
        RunStats original = new RunStats(12, 2, 45L, 3, 5);
        String json = mapper.writeValueAsString(original);
        RunStats back = mapper.readValue(json, RunStats.class);
        assertEquals(original, back);
    }

    @Test
    public void runStats_empty() {
        RunStats empty = RunStats.empty();
        assertEquals(0, empty.totalSteps());
        assertEquals(0, empty.blockedCount());
        assertEquals(0L, empty.durationMs());
        assertEquals(0, empty.roverCount());
        assertEquals(0, empty.obstacleCount());
    }

    @Test
    public void webError_roundTrip() throws Exception {
        WebError original = new WebError("INVALID_GRID", "width and height must both be provided");
        String json = mapper.writeValueAsString(original);
        WebError back = mapper.readValue(json, WebError.class);
        assertEquals(original, back);
    }

    @Test
    public void arenaConfig_bounded_roundTrip() throws Exception {
        ArenaConfig original = new ArenaConfig(
                10, 10, false,
                List.of(new PositionDto(3, 3)),
                "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMR")),
                false
        );
        String json = mapper.writeValueAsString(original);
        ArenaConfig back = mapper.readValue(json, ArenaConfig.class);
        assertEquals(original, back);
    }

    @Test
    public void arenaConfig_unbounded_nullDimensions_roundTrip() throws Exception {
        ArenaConfig original = new ArenaConfig(
                null, null, false,
                List.of(),
                "skip",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "M")),
                true
        );
        String json = mapper.writeValueAsString(original);
        ArenaConfig back = mapper.readValue(json, ArenaConfig.class);
        assertNull(back.width());
        assertNull(back.height());
        assertTrue(back.isUnbounded());
    }

    @Test
    public void arenaConfig_isUnbounded() {
        ArenaConfig unbounded = new ArenaConfig(null, null, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "")), false);
        assertTrue(unbounded.isUnbounded());

        ArenaConfig bounded = new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "")), false);
        assertFalse(bounded.isUnbounded());
    }

    @Test
    public void sessionSnapshot_roundTrip() throws Exception {
        SessionSnapshot original = new SessionSnapshot(
                "sess-1",
                new ArenaConfig(10, 10, false, List.of(), "fail",
                        List.of(new RoverSpecDto("R1", 0, 0, "N", "MM")), false),
                Map.of("R1", new RoverStateDto(0, 2, "NORTH")),
                Map.of("R1", List.of(new PositionDto(0, 0), new PositionDto(0, 1), new PositionDto(0, 2))),
                new ViewportDto(0, 0, 9, 9),
                new RunStats(2, 0, 3L, 1, 0),
                false
        );
        String json = mapper.writeValueAsString(original);
        SessionSnapshot back = mapper.readValue(json, SessionSnapshot.class);
        assertEquals(original.sessionId(), back.sessionId());
        assertEquals(original.config(), back.config());
        assertEquals(original.rovers(), back.rovers());
        assertEquals(original.viewport(), back.viewport());
        assertEquals(original.stats(), back.stats());
        assertEquals(original.running(), back.running());
    }
}
