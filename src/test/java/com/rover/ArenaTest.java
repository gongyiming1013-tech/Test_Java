package com.rover;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/** Tests for {@link Arena}. */
public class ArenaTest {

    // --- Rover lifecycle ---

    @Test
    public void createRover_registersWithId() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        Rover rover = arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        assertNotNull(rover);
        assertEquals(new Position(0, 0), arena.getRover("R1").getPosition());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRover_duplicateId_throws() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R1", new Position(1, 1), Direction.EAST);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRover_occupiedPosition_throws() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(0, 0), Direction.EAST);
    }

    @Test
    public void removeRover_removesFromRegistry() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.removeRover("R1");

        // Position (0,0) is now free
        arena.createRover("R2", new Position(0, 0), Direction.EAST);
        assertEquals(new Position(0, 0), arena.getRover("R2").getPosition());
    }

    @Test(expected = IllegalArgumentException.class)
    public void removeRover_nonExistent_throws() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.removeRover("R1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void getRover_nonExistent_throws() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.getRover("R1");
    }

    @Test
    public void getPositions_returnsAllRoverPositions() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(3, 4), Direction.EAST);

        Map<String, Position> positions = arena.getPositions();
        assertEquals(2, positions.size());
        assertEquals(new Position(0, 0), positions.get("R1"));
        assertEquals(new Position(3, 4), positions.get("R2"));
    }

    // --- Sequential execution ---

    @Test
    public void sequential_twoRovers_correctFinalPositions() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(5, 5), Direction.SOUTH);

        Map<String, List<Action>> commands = new LinkedHashMap<>();
        commands.put("R1", List.of(new MoveForwardAction(), new MoveForwardAction()));
        commands.put("R2", List.of(new MoveForwardAction(), new TurnRightAction()));

        arena.executeSequential(commands);

        assertEquals(new Position(0, 2), arena.getRover("R1").getPosition());
        assertEquals(new Position(5, 4), arena.getRover("R2").getPosition());
        assertEquals(Direction.WEST, arena.getRover("R2").getDirection());
    }

    @Test
    public void sequential_firstRoverAffectsSecond() {
        // R1 at (0,0) facing east, R2 at (2,0) facing west
        // Sequential: R1 moves east twice → (2,0)? No, R2 is there → collision
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.SKIP);
        arena.createRover("R1", new Position(0, 0), Direction.EAST);
        arena.createRover("R2", new Position(2, 0), Direction.WEST);

        Map<String, List<Action>> commands = new LinkedHashMap<>();
        commands.put("R1", List.of(new MoveForwardAction(), new MoveForwardAction()));
        commands.put("R2", List.of(new MoveForwardAction()));

        arena.executeSequential(commands);

        // R1: (0,0) → (1,0) → tries (2,0) blocked by R2 → stays (1,0)
        assertEquals(new Position(1, 0), arena.getRover("R1").getPosition());
        // R2: (2,0) → tries (1,0) blocked by R1 → stays (2,0)
        assertEquals(new Position(2, 0), arena.getRover("R2").getPosition());
    }

    @Test
    public void sequential_emptyCommands_noChange() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);

        arena.executeSequential(Map.of());
        assertEquals(new Position(0, 0), arena.getRover("R1").getPosition());
    }

    // --- Parallel execution ---

    @Test
    public void parallel_roundRobin_stepping() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(5, 0), Direction.NORTH);

        Map<String, List<Action>> commands = new LinkedHashMap<>();
        commands.put("R1", List.of(new MoveForwardAction(), new MoveForwardAction()));
        commands.put("R2", List.of(new MoveForwardAction(), new MoveForwardAction()));

        arena.executeParallel(commands);

        // Both moved north 2 steps
        assertEquals(new Position(0, 2), arena.getRover("R1").getPosition());
        assertEquals(new Position(5, 2), arena.getRover("R2").getPosition());
    }

    @Test
    public void parallel_collisionInSameRound() {
        // R1 at (0,0) facing east, R2 at (2,0) facing west
        // Both move toward each other: round 1 → R1(1,0), R2(1,0) collision!
        // R1 processed first → moves to (1,0). R2 tries (1,0) → blocked by R1.
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.SKIP);
        arena.createRover("R1", new Position(0, 0), Direction.EAST);
        arena.createRover("R2", new Position(2, 0), Direction.WEST);

        Map<String, List<Action>> commands = new LinkedHashMap<>();
        commands.put("R1", List.of(new MoveForwardAction()));
        commands.put("R2", List.of(new MoveForwardAction()));

        arena.executeParallel(commands);

        assertEquals(new Position(1, 0), arena.getRover("R1").getPosition()); // moved
        assertEquals(new Position(2, 0), arena.getRover("R2").getPosition()); // blocked
    }

    @Test
    public void parallel_unevenCommandLengths() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(5, 0), Direction.NORTH);

        Map<String, List<Action>> commands = new LinkedHashMap<>();
        commands.put("R1", List.of(new MoveForwardAction(), new MoveForwardAction(), new MoveForwardAction()));
        commands.put("R2", List.of(new MoveForwardAction()));

        arena.executeParallel(commands);

        assertEquals(new Position(0, 3), arena.getRover("R1").getPosition());
        assertEquals(new Position(5, 1), arena.getRover("R2").getPosition());
    }

    // --- Combined constraints ---

    @Test
    public void arena_withGrid_andObstacles() {
        Environment grid = new GridEnvironment(5, 5, Set.of(new Position(2, 2)), BoundaryMode.BOUNDED);
        Arena arena = new Arena(grid, ConflictPolicy.SKIP);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        arena.createRover("R2", new Position(4, 4), Direction.SOUTH);

        Map<String, List<Action>> commands = new LinkedHashMap<>();
        // R1 moves north 5 times — hits boundary at y=5
        commands.put("R1", List.of(
                new MoveForwardAction(), new MoveForwardAction(),
                new MoveForwardAction(), new MoveForwardAction(),
                new MoveForwardAction()));
        commands.put("R2", List.of(new MoveForwardAction(), new MoveForwardAction()));

        arena.executeSequential(commands);

        assertEquals(new Position(0, 4), arena.getRover("R1").getPosition()); // boundary blocked
        assertEquals(new Position(4, 2), arena.getRover("R2").getPosition());
    }

    // --- isOccupied ---

    @Test
    public void isOccupied_emptyArena_false() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        assertFalse(arena.isOccupied(new Position(0, 0), null));
    }

    @Test
    public void isOccupied_withRover_true() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        assertTrue(arena.isOccupied(new Position(0, 0), null));
    }

    @Test
    public void isOccupied_excludeSelf_false() {
        Arena arena = new Arena(new UnboundedEnvironment(), ConflictPolicy.FAIL);
        arena.createRover("R1", new Position(0, 0), Direction.NORTH);
        // Exclude the rover at (0,0) — not occupied by others
        assertFalse(arena.isOccupied(new Position(0, 0), new Position(0, 0)));
    }
}
