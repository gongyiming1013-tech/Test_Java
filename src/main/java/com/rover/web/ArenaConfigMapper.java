package com.rover.web;

import com.rover.ActionParser;
import com.rover.Arena;
import com.rover.BoundaryMode;
import com.rover.ConflictPolicy;
import com.rover.Direction;
import com.rover.Environment;
import com.rover.GridEnvironment;
import com.rover.InvalidActionException;
import com.rover.Position;
import com.rover.Rover;
import com.rover.UnboundedEnvironment;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure static helper that validates an {@link ArenaConfig} DTO and maps it
 * to domain objects ({@link Environment}, {@link ConflictPolicy}, {@link Arena}).
 *
 * <p>All validation failures throw {@link ConfigValidationException} with a
 * specific {@code code}, which the REST layer translates into a uniform
 * {@link WebError} response.</p>
 */
public final class ArenaConfigMapper {

    private ArenaConfigMapper() {
        // Utility class
    }

    /**
     * Builds the environment implied by the config.
     *
     * <p>Both dimensions null → {@link com.rover.UnboundedEnvironment}.
     * Both dimensions positive → {@link com.rover.GridEnvironment}.
     * Any other combination throws {@link ConfigValidationException} with
     * code {@code INVALID_GRID}.</p>
     *
     * @param config the config to inspect
     * @return the constructed environment
     * @throws ConfigValidationException on invalid dimensions or bad obstacle coordinates
     */
    public static Environment buildEnvironment(ArenaConfig config) {
        Integer w = config.width();
        Integer h = config.height();

        if (w == null && h == null) {
            return new UnboundedEnvironment();
        }
        if (w == null || h == null) {
            throw new ConfigValidationException("INVALID_GRID",
                    "width and height must both be provided, or both be omitted for unbounded mode");
        }
        if (w <= 0 || h <= 0) {
            throw new ConfigValidationException("INVALID_GRID",
                    "grid dimensions must be positive (got " + w + "x" + h + ")");
        }

        Set<Position> obstacles = new HashSet<>();
        List<PositionDto> obsList = config.obstacles();
        if (obsList != null) {
            for (PositionDto p : obsList) {
                obstacles.add(new Position(p.x(), p.y()));
            }
        }

        BoundaryMode mode = config.wrap() ? BoundaryMode.WRAP : BoundaryMode.BOUNDED;
        return new GridEnvironment(w, h, obstacles, mode);
    }

    /**
     * Parses a conflict policy name (case-insensitive).
     *
     * @param name {@code "fail"}, {@code "skip"}, or {@code "reverse"}
     * @return the matching enum value
     * @throws ConfigValidationException with code {@code UNKNOWN_POLICY} on an unknown name
     */
    public static ConflictPolicy buildConflictPolicy(String name) {
        if (name == null) {
            throw new ConfigValidationException("UNKNOWN_POLICY",
                    "conflictPolicy is required (fail, skip, or reverse)");
        }
        return switch (name.toLowerCase()) {
            case "fail"    -> ConflictPolicy.FAIL;
            case "skip"    -> ConflictPolicy.SKIP;
            case "reverse" -> ConflictPolicy.REVERSE;
            default -> throw new ConfigValidationException("UNKNOWN_POLICY",
                    "unknown conflictPolicy: " + name + " (expected fail, skip, or reverse)");
        };
    }

    /**
     * Parses a direction string.
     *
     * @param s {@code "N"}, {@code "E"}, {@code "S"}, or {@code "W"} (case-insensitive)
     * @return the matching enum value
     * @throws ConfigValidationException with code {@code UNKNOWN_DIRECTION} on an unknown value
     */
    public static Direction parseDirection(String s) {
        if (s == null) {
            throw new ConfigValidationException("UNKNOWN_DIRECTION",
                    "direction is required (N, E, S, or W)");
        }
        return switch (s.toUpperCase()) {
            case "N" -> Direction.NORTH;
            case "E" -> Direction.EAST;
            case "S" -> Direction.SOUTH;
            case "W" -> Direction.WEST;
            default -> throw new ConfigValidationException("UNKNOWN_DIRECTION",
                    "unknown direction: " + s + " (expected N, E, S, or W)");
        };
    }

    /**
     * Validates the config and constructs a fully populated {@link Arena}
     * with all rovers registered and their commands parsed.
     *
     * @param config the config to build from
     * @return the constructed arena
     * @throws ConfigValidationException on any validation failure
     */
    public static Arena buildArena(ArenaConfig config) {
        List<RoverSpecDto> rovers = config.rovers();
        if (rovers == null || rovers.isEmpty()) {
            throw new ConfigValidationException("NO_ROVERS",
                    "at least one rover is required");
        }

        // Validate unique rover IDs up front
        Set<String> seenIds = new LinkedHashSet<>();
        for (RoverSpecDto spec : rovers) {
            if (!seenIds.add(spec.id())) {
                throw new ConfigValidationException("DUPLICATE_ROVER_ID",
                        "duplicate rover id: " + spec.id());
            }
        }

        Environment environment = buildEnvironment(config);
        ConflictPolicy policy = buildConflictPolicy(config.conflictPolicy());
        Arena arena = new Arena(environment, policy);

        ActionParser parser = new ActionParser();
        for (RoverSpecDto spec : rovers) {
            Direction dir = parseDirection(spec.direction());
            Rover rover = arena.createRover(spec.id(), new Position(spec.x(), spec.y()), dir);

            // Validate commands by parsing them — throws InvalidActionException on unknown chars
            try {
                parser.parse(spec.commands() == null ? "" : spec.commands());
            } catch (InvalidActionException e) {
                throw new ConfigValidationException("INVALID_COMMAND",
                        "rover " + spec.id() + ": " + e.getMessage());
            }
        }

        return arena;
    }
}
