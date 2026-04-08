package com.rover;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CLI entry point for the rover control system.
 *
 * <p>Accepts a command string and optional flags for grid constraints:</p>
 * <pre>
 *   java -jar rover.jar [--grid WxH] [--wrap] [--obstacles "x1,y1;x2,y2"] [--on-conflict fail|skip|reverse] "COMMANDS"
 * </pre>
 */
public class App {

    /**
     * Runs the rover with the given command string and no constraints.
     *
     * @param commands the command string (e.g. "MMRMMLM"), or null
     * @return the final position as "x:y"
     * @throws InvalidActionException if the commands contain invalid characters
     */
    public static String run(String commands) {
        return run(commands, null, false, null, ConflictPolicy.FAIL);
    }

    /**
     * Runs the rover with the given command string and environment configuration.
     *
     * @param commands       the command string
     * @param gridSpec       grid dimensions as "WxH" (e.g. "10x10"), or null for unbounded
     * @param wrap           true for toroidal wrapping at boundaries
     * @param obstaclesSpec  obstacles as "x1,y1;x2,y2" or null for none
     * @param conflictPolicy how to handle blocked moves
     * @return the final position as "x:y"
     */
    public static String run(String commands, String gridSpec, boolean wrap,
                             String obstaclesSpec, ConflictPolicy conflictPolicy) {
        Environment environment = buildEnvironment(gridSpec, wrap, obstaclesSpec);

        ActionParser parser = new ActionParser();
        List<Action> actions = parser.parse(commands);

        Rover rover = new Rover(new Position(0, 0), Direction.NORTH, environment, conflictPolicy);
        rover.execute(actions);

        Position pos = rover.getPosition();
        return pos.x() + ":" + pos.y();
    }

    /** CLI entry point. Parses flags and command string from arguments. */
    public static void main(String[] args) {
        String commands = "";
        String gridSpec = null;
        boolean wrap = false;
        String obstaclesSpec = null;
        ConflictPolicy conflictPolicy = ConflictPolicy.FAIL;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--grid" -> gridSpec = args[++i];
                case "--wrap" -> wrap = true;
                case "--obstacles" -> obstaclesSpec = args[++i];
                case "--on-conflict" -> conflictPolicy = ConflictPolicy.valueOf(args[++i].toUpperCase());
                default -> commands = args[i];
            }
        }

        String result = run(commands, gridSpec, wrap, obstaclesSpec, conflictPolicy);
        System.out.println(result);
    }

    private static Environment buildEnvironment(String gridSpec, boolean wrap, String obstaclesSpec) {
        if (gridSpec == null) {
            return new UnboundedEnvironment();
        }

        String[] dims = gridSpec.toLowerCase().split("x");
        if (dims.length != 2) {
            throw new IllegalArgumentException("Invalid grid format: " + gridSpec + " (expected WxH, e.g. 10x10)");
        }
        int width = Integer.parseInt(dims[0]);
        int height = Integer.parseInt(dims[1]);

        Set<Position> obstacles = parseObstacles(obstaclesSpec);
        BoundaryMode boundaryMode = wrap ? BoundaryMode.WRAP : BoundaryMode.BOUNDED;

        return new GridEnvironment(width, height, obstacles, boundaryMode);
    }

    private static Set<Position> parseObstacles(String spec) {
        if (spec == null || spec.isEmpty()) {
            return Set.of();
        }

        Set<Position> obstacles = new HashSet<>();
        String[] pairs = spec.split(";");
        for (String pair : pairs) {
            String[] coords = pair.split(",");
            if (coords.length != 2) {
                throw new IllegalArgumentException("Invalid obstacle format: " + pair + " (expected x,y)");
            }
            obstacles.add(new Position(Integer.parseInt(coords[0].trim()), Integer.parseInt(coords[1].trim())));
        }
        return obstacles;
    }
}
