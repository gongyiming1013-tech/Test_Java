package com.rover;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fleet manager for multiple rovers on a shared grid.
 *
 * <p>Each rover is assigned an {@link ArenaEnvironment} that transparently
 * handles inter-rover collision detection. Supports sequential and
 * parallel (round-robin) execution modes.</p>
 */
public class Arena {

    private final Map<String, Rover> rovers = new LinkedHashMap<>();
    private final Environment baseEnvironment;
    private final ConflictPolicy conflictPolicy;

    /**
     * Creates an arena with the given environment and conflict policy.
     *
     * @param baseEnvironment shared environment (grid/obstacles/unbounded)
     * @param conflictPolicy  how to handle blocked moves (boundary, obstacle, or collision)
     */
    public Arena(Environment baseEnvironment, ConflictPolicy conflictPolicy) {
        this.baseEnvironment = baseEnvironment;
        this.conflictPolicy = conflictPolicy;
    }

    /**
     * Creates and registers a rover with a unique ID.
     *
     * @param id        unique rover identifier
     * @param position  starting position
     * @param direction starting direction
     * @return the created rover
     * @throws IllegalArgumentException if ID already exists or position is occupied
     */
    public Rover createRover(String id, Position position, Direction direction) {
        if (rovers.containsKey(id)) {
            throw new IllegalArgumentException("Rover ID already exists: " + id);
        }
        if (isOccupied(position, null)) {
            throw new IllegalArgumentException("Position already occupied: " + position.x() + "," + position.y());
        }
        ArenaEnvironment arenaEnv = new ArenaEnvironment(baseEnvironment, this);
        Rover rover = new Rover(position, direction, arenaEnv, conflictPolicy);
        rovers.put(id, rover);
        return rover;
    }

    /**
     * Removes a rover from the arena.
     *
     * @param id the rover ID to remove
     * @throws IllegalArgumentException if the ID does not exist
     */
    public void removeRover(String id) {
        if (!rovers.containsKey(id)) {
            throw new IllegalArgumentException("Rover not found: " + id);
        }
        rovers.remove(id);
    }

    /**
     * Retrieves a rover by ID.
     *
     * @param id the rover ID
     * @return the rover
     * @throws IllegalArgumentException if the ID does not exist
     */
    public Rover getRover(String id) {
        Rover rover = rovers.get(id);
        if (rover == null) {
            throw new IllegalArgumentException("Rover not found: " + id);
        }
        return rover;
    }

    /**
     * Returns a snapshot of all rover positions.
     *
     * @return map of rover ID to current position
     */
    public Map<String, Position> getPositions() {
        Map<String, Position> positions = new LinkedHashMap<>();
        for (Map.Entry<String, Rover> entry : rovers.entrySet()) {
            positions.put(entry.getKey(), entry.getValue().getPosition());
        }
        return positions;
    }

    /**
     * Returns a snapshot of all rover states (position + direction).
     *
     * @return map of rover ID to current state
     */
    public Map<String, RoverState> getAllStates() {
        Map<String, RoverState> states = new LinkedHashMap<>();
        for (Map.Entry<String, Rover> entry : rovers.entrySet()) {
            states.put(entry.getKey(), entry.getValue().getState());
        }
        return states;
    }

    /**
     * Executes each rover's commands in full, one rover at a time.
     *
     * @param commands map of rover ID to action list
     */
    public void executeSequential(Map<String, List<Action>> commands) {
        for (Map.Entry<String, List<Action>> entry : commands.entrySet()) {
            Rover rover = getRover(entry.getKey());
            rover.execute(entry.getValue());
        }
    }

    /**
     * Executes commands round-robin: one step per rover per round.
     * In each round, rovers are processed in insertion order.
     *
     * @param commands map of rover ID to action list
     */
    public void executeParallel(Map<String, List<Action>> commands) {
        int maxSteps = commands.values().stream().mapToInt(List::size).max().orElse(0);

        for (int step = 0; step < maxSteps; step++) {
            for (Map.Entry<String, List<Action>> entry : commands.entrySet()) {
                List<Action> actions = entry.getValue();
                if (step < actions.size()) {
                    Rover rover = getRover(entry.getKey());
                    rover.execute(actions.get(step));
                }
            }
        }
    }

    /**
     * Checks whether a position is occupied by any rover.
     *
     * @param position    the position to check
     * @param excludeSelf position of the moving rover (excluded from check), or null to check all
     * @return true if any other rover occupies the position
     */
    public boolean isOccupied(Position position, Position excludeSelf) {
        for (Rover rover : rovers.values()) {
            Position roverPos = rover.getPosition();
            if (roverPos.equals(position)) {
                if (excludeSelf == null || !roverPos.equals(excludeSelf)) {
                    return true;
                }
            }
        }
        return false;
    }
}
