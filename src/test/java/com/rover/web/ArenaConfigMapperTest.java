package com.rover.web;

import com.rover.Arena;
import com.rover.BoundaryMode;
import com.rover.ConflictPolicy;
import com.rover.Direction;
import com.rover.Environment;
import com.rover.GridEnvironment;
import com.rover.UnboundedEnvironment;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** Contract tests for {@link ArenaConfigMapper}. */
public class ArenaConfigMapperTest {

    // --- buildEnvironment ---

    @Test
    public void buildEnvironment_bothDimsNull_returnsUnbounded() {
        ArenaConfig config = cfg(null, null, false, List.of(), "fail",
                List.of(rover("R1", 0, 0, "N", "")), false);
        Environment env = ArenaConfigMapper.buildEnvironment(config);
        assertTrue(env instanceof UnboundedEnvironment);
    }

    @Test
    public void buildEnvironment_bothDimsPositive_returnsGrid() {
        ArenaConfig config = cfg(10, 15, false, List.of(), "fail",
                List.of(rover("R1", 0, 0, "N", "")), false);
        Environment env = ArenaConfigMapper.buildEnvironment(config);
        assertTrue(env instanceof GridEnvironment);
        GridEnvironment grid = (GridEnvironment) env;
        assertEquals(10, grid.getWidth());
        assertEquals(15, grid.getHeight());
    }

    @Test
    public void buildEnvironment_bothDimsWithWrap_returnsWrapGrid() {
        ArenaConfig config = cfg(5, 5, true, List.of(), "fail",
                List.of(rover("R1", 0, 0, "N", "")), false);
        Environment env = ArenaConfigMapper.buildEnvironment(config);
        assertTrue(env instanceof GridEnvironment);
        assertEquals(BoundaryMode.WRAP, ((GridEnvironment) env).getBoundaryMode());
    }

    @Test(expected = ConfigValidationException.class)
    public void buildEnvironment_widthNullHeightSet_throws() {
        ArenaConfig config = cfg(null, 10, false, List.of(), "fail",
                List.of(rover("R1", 0, 0, "N", "")), false);
        ArenaConfigMapper.buildEnvironment(config);
    }

    @Test(expected = ConfigValidationException.class)
    public void buildEnvironment_widthSetHeightNull_throws() {
        ArenaConfig config = cfg(10, null, false, List.of(), "fail",
                List.of(rover("R1", 0, 0, "N", "")), false);
        ArenaConfigMapper.buildEnvironment(config);
    }

    @Test
    public void buildEnvironment_partialDims_throwsWithInvalidGridCode() {
        ArenaConfig config = cfg(10, null, false, List.of(), "fail",
                List.of(rover("R1", 0, 0, "N", "")), false);
        try {
            ArenaConfigMapper.buildEnvironment(config);
            fail("should have thrown");
        } catch (ConfigValidationException e) {
            assertEquals("INVALID_GRID", e.getCode());
        }
    }

    @Test(expected = ConfigValidationException.class)
    public void buildEnvironment_zeroDimensions_throws() {
        ArenaConfig config = cfg(0, 0, false, List.of(), "fail",
                List.of(rover("R1", 0, 0, "N", "")), false);
        ArenaConfigMapper.buildEnvironment(config);
    }

    @Test(expected = ConfigValidationException.class)
    public void buildEnvironment_negativeDimensions_throws() {
        ArenaConfig config = cfg(-5, 10, false, List.of(), "fail",
                List.of(rover("R1", 0, 0, "N", "")), false);
        ArenaConfigMapper.buildEnvironment(config);
    }

    // --- buildConflictPolicy ---

    @Test
    public void buildConflictPolicy_fail() {
        assertEquals(ConflictPolicy.FAIL, ArenaConfigMapper.buildConflictPolicy("fail"));
    }

    @Test
    public void buildConflictPolicy_skip() {
        assertEquals(ConflictPolicy.SKIP, ArenaConfigMapper.buildConflictPolicy("skip"));
    }

    @Test
    public void buildConflictPolicy_reverse() {
        assertEquals(ConflictPolicy.REVERSE, ArenaConfigMapper.buildConflictPolicy("reverse"));
    }

    @Test
    public void buildConflictPolicy_caseInsensitive() {
        assertEquals(ConflictPolicy.FAIL, ArenaConfigMapper.buildConflictPolicy("FAIL"));
        assertEquals(ConflictPolicy.SKIP, ArenaConfigMapper.buildConflictPolicy("Skip"));
    }

    @Test
    public void buildConflictPolicy_unknown_throwsWithCode() {
        try {
            ArenaConfigMapper.buildConflictPolicy("neon");
            fail("should have thrown");
        } catch (ConfigValidationException e) {
            assertEquals("UNKNOWN_POLICY", e.getCode());
        }
    }

    // --- parseDirection ---

    @Test
    public void parseDirection_allFour() {
        assertEquals(Direction.NORTH, ArenaConfigMapper.parseDirection("N"));
        assertEquals(Direction.EAST,  ArenaConfigMapper.parseDirection("E"));
        assertEquals(Direction.SOUTH, ArenaConfigMapper.parseDirection("S"));
        assertEquals(Direction.WEST,  ArenaConfigMapper.parseDirection("W"));
    }

    @Test
    public void parseDirection_caseInsensitive() {
        assertEquals(Direction.NORTH, ArenaConfigMapper.parseDirection("n"));
        assertEquals(Direction.EAST,  ArenaConfigMapper.parseDirection("e"));
    }

    @Test
    public void parseDirection_unknown_throwsWithCode() {
        try {
            ArenaConfigMapper.parseDirection("X");
            fail("should have thrown");
        } catch (ConfigValidationException e) {
            assertEquals("UNKNOWN_DIRECTION", e.getCode());
        }
    }

    // --- buildArena ---

    @Test
    public void buildArena_singleRover_createsArenaWithRover() {
        ArenaConfig config = cfg(10, 10, false, List.of(), "fail",
                List.of(rover("R1", 2, 3, "N", "MMR")), false);
        Arena arena = ArenaConfigMapper.buildArena(config);
        assertNotNull(arena.getRover("R1"));
    }

    @Test
    public void buildArena_multipleRovers() {
        ArenaConfig config = cfg(10, 10, false, List.of(), "fail",
                List.of(
                        rover("R1", 0, 0, "N", "M"),
                        rover("R2", 5, 5, "E", "M")
                ), false);
        Arena arena = ArenaConfigMapper.buildArena(config);
        assertNotNull(arena.getRover("R1"));
        assertNotNull(arena.getRover("R2"));
    }

    @Test
    public void buildArena_withObstacles() {
        ArenaConfig config = cfg(10, 10, false,
                List.of(new PositionDto(3, 3), new PositionDto(4, 4)),
                "fail",
                List.of(rover("R1", 0, 0, "N", "M")), false);
        Arena arena = ArenaConfigMapper.buildArena(config);
        assertNotNull(arena);
    }

    @Test
    public void buildArena_emptyRoverList_throws() {
        ArenaConfig config = cfg(10, 10, false, List.of(), "fail", List.of(), false);
        try {
            ArenaConfigMapper.buildArena(config);
            fail("should have thrown");
        } catch (ConfigValidationException e) {
            assertEquals("NO_ROVERS", e.getCode());
        }
    }

    @Test
    public void buildArena_duplicateRoverId_throws() {
        ArenaConfig config = cfg(10, 10, false, List.of(), "fail",
                List.of(
                        rover("R1", 0, 0, "N", "M"),
                        rover("R1", 5, 5, "E", "M")
                ), false);
        try {
            ArenaConfigMapper.buildArena(config);
            fail("should have thrown");
        } catch (ConfigValidationException e) {
            assertEquals("DUPLICATE_ROVER_ID", e.getCode());
        }
    }

    @Test(expected = ConfigValidationException.class)
    public void buildArena_invalidCommandChar_throws() {
        ArenaConfig config = cfg(10, 10, false, List.of(), "fail",
                List.of(rover("R1", 0, 0, "N", "MMX")), false);
        ArenaConfigMapper.buildArena(config);
    }

    @Test
    public void buildArena_unboundedConfig_works() {
        ArenaConfig config = cfg(null, null, false, List.of(), "fail",
                List.of(rover("R1", 0, 0, "N", "MMR")), false);
        Arena arena = ArenaConfigMapper.buildArena(config);
        assertNotNull(arena);
        assertNotNull(arena.getRover("R1"));
    }

    // --- helpers ---

    private static ArenaConfig cfg(Integer w, Integer h, boolean wrap, List<PositionDto> obs,
                                    String policy, List<RoverSpecDto> rovers, boolean parallel) {
        return new ArenaConfig(w, h, wrap, obs, policy, rovers, parallel);
    }

    private static RoverSpecDto rover(String id, int x, int y, String dir, String cmds) {
        return new RoverSpecDto(id, x, y, dir, cmds);
    }
}
