package com.rover.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Contract tests for {@link RoverEventDto} JSON shape. Field names and types
 * are part of the frontend contract — these tests pin them down.
 */
public class RoverEventDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void allFieldsSerializedWithExpectedNames() throws Exception {
        RoverEventDto dto = new RoverEventDto(
                "R1", 2, 5,
                1, 2, "NORTH",
                1, 3, "NORTH",
                "MoveForward",
                false);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(dto));

        assertEquals("R1", json.get("roverId").asText());
        assertEquals(2, json.get("stepIndex").asInt());
        assertEquals(5, json.get("totalSteps").asInt());
        assertEquals(1, json.get("prevX").asInt());
        assertEquals(2, json.get("prevY").asInt());
        assertEquals("NORTH", json.get("prevDir").asText());
        assertEquals(1, json.get("newX").asInt());
        assertEquals(3, json.get("newY").asInt());
        assertEquals("NORTH", json.get("newDir").asText());
        assertEquals("MoveForward", json.get("action").asText());
        assertFalse(json.get("blocked").asBoolean());
    }

    @Test
    public void blockedTruePreservedThroughJson() throws Exception {
        RoverEventDto dto = new RoverEventDto(
                "R2", 0, 1,
                3, 3, "EAST",
                3, 3, "EAST",
                "MoveForward",
                true);

        String json = mapper.writeValueAsString(dto);
        RoverEventDto round = mapper.readValue(json, RoverEventDto.class);

        assertTrue(round.blocked());
        assertEquals(dto, round);
    }

    @Test
    public void roundTripPreservesAllFields() throws Exception {
        RoverEventDto dto = new RoverEventDto(
                "alpha", 7, 10,
                -2, 4, "WEST",
                -3, 4, "WEST",
                "Backward",
                false);

        String json = mapper.writeValueAsString(dto);
        RoverEventDto round = mapper.readValue(json, RoverEventDto.class);

        assertEquals(dto, round);
    }
}
