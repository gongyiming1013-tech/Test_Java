package com.rover;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CLI entry point for the rover control system.
 *
 * <p>Accepts a command string and optional flags for grid constraints:</p>
 * <pre>
 *   java -jar rover.jar [--grid WxH] [--wrap] [--obstacles "x1,y1;x2,y2"] [--on-conflict fail|skip|reverse] [--visual] [--delay ms] [--theme modern|minimal|mono] "COMMANDS"
 *   java -jar rover.jar --arena [--grid WxH] [--parallel] [--theme modern|minimal|mono] [--rover "ID:x,y,dir:commands"] ...
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
        boolean visual = false;
        boolean verbose = false;
        long delayMs = 500;
        String themeName = null;
        boolean arenaMode = false;
        boolean parallel = false;
        List<String> roverSpecs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--grid" -> gridSpec = args[++i];
                case "--wrap" -> wrap = true;
                case "--obstacles" -> obstaclesSpec = args[++i];
                case "--on-conflict" -> conflictPolicy = ConflictPolicy.valueOf(args[++i].toUpperCase());
                case "--visual" -> visual = true;
                case "--verbose" -> verbose = true;
                case "--delay" -> delayMs = Long.parseLong(args[++i]);
                case "--theme" -> themeName = args[++i];
                case "--arena" -> arenaMode = true;
                case "--parallel" -> parallel = true;
                case "--rover" -> roverSpecs.add(args[++i]);
                default -> commands = args[i];
            }
        }

        Theme theme = resolveTheme(themeName);

        if (arenaMode) {
            runArena(gridSpec, wrap, obstaclesSpec, conflictPolicy, visual, delayMs, parallel, roverSpecs, theme);
        } else if (visual) {
            runVisual(commands, gridSpec, wrap, obstaclesSpec, conflictPolicy, delayMs, theme);
        } else if (verbose) {
            runVerbose(commands, gridSpec, wrap, obstaclesSpec, conflictPolicy);
        } else {
            String result = run(commands, gridSpec, wrap, obstaclesSpec, conflictPolicy);
            System.out.println(result);
        }
    }

    private static void runVerbose(String commands, String gridSpec, boolean wrap,
                                    String obstaclesSpec, ConflictPolicy conflictPolicy) {
        Environment environment = buildEnvironment(gridSpec, wrap, obstaclesSpec);
        ActionParser parser = new ActionParser();
        List<Action> actions = parser.parse(commands);

        Rover rover = new Rover(new Position(0, 0), Direction.NORTH, environment, conflictPolicy);
        rover.addListener(new VerboseListener());

        try {
            rover.execute(actions);
        } catch (MoveBlockedException e) {
            System.out.println("Execution stopped: " + e.getMessage());
        }

        Position pos = rover.getPosition();
        System.out.println(pos.x() + ":" + pos.y());
    }

    private static void runVisual(String commands, String gridSpec, boolean wrap,
                                  String obstaclesSpec, ConflictPolicy conflictPolicy,
                                  long delayMs, Theme theme) {
        Environment environment = buildEnvironment(gridSpec, wrap, obstaclesSpec);
        ActionParser parser = new ActionParser();
        List<Action> actions = parser.parse(commands);

        Rover rover = new Rover(new Position(0, 0), Direction.NORTH, environment, conflictPolicy);

        int viewWidth;
        int viewHeight;
        Set<Position> obstacleSet;
        if (environment instanceof GridEnvironment ge) {
            viewWidth = ge.getWidth();
            viewHeight = ge.getHeight();
            obstacleSet = ge.getObstacles();
        } else {
            viewWidth = 20;
            viewHeight = 20;
            obstacleSet = Set.of();
        }

        TerminalRenderer renderer = new TerminalRenderer(viewWidth, viewHeight, obstacleSet,
                System.out, theme, commands);
        rover.addListener(renderer);

        StepExecutor executor = new StepExecutor(rover, delayMs);
        try {
            executor.execute(actions);
        } catch (MoveBlockedException e) {
            System.out.println("Execution stopped: " + e.getMessage());
        }
    }

    private static void runArena(String gridSpec, boolean wrap, String obstaclesSpec,
                                  ConflictPolicy conflictPolicy, boolean visual, long delayMs,
                                  boolean parallel, List<String> roverSpecs, Theme theme) {
        Environment baseEnv = buildEnvironment(gridSpec, wrap, obstaclesSpec);
        Arena arena = new Arena(baseEnv, conflictPolicy);
        ActionParser parser = new ActionParser();

        int viewWidth = (baseEnv instanceof GridEnvironment ge) ? ge.getWidth() : 20;
        int viewHeight = (baseEnv instanceof GridEnvironment ge) ? ge.getHeight() : 20;
        Set<Position> obstacleSet = (baseEnv instanceof GridEnvironment ge) ? ge.getObstacles() : Set.of();

        ArenaRenderer arenaRenderer = visual
                ? new ArenaRenderer(viewWidth, viewHeight, obstacleSet, arena, System.out, theme)
                : null;

        Map<String, List<Action>> commands = new LinkedHashMap<>();
        for (String spec : roverSpecs) {
            String[] parts = spec.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid rover spec: " + spec + " (expected ID:x,y,dir:commands)");
            }
            String id = parts[0];
            String[] posDir = parts[1].split(",");
            if (posDir.length != 3) {
                throw new IllegalArgumentException("Invalid rover position: " + parts[1] + " (expected x,y,dir)");
            }
            int x = Integer.parseInt(posDir[0].trim());
            int y = Integer.parseInt(posDir[1].trim());
            Direction dir = parseDirection(posDir[2].trim());

            Rover rover = arena.createRover(id, new Position(x, y), dir);
            commands.put(id, parser.parse(parts[2]));

            if (arenaRenderer != null) {
                rover.addListener(arenaRenderer.listenerFor(id));
            }
        }

        try {
            if (parallel) {
                arena.executeParallel(commands);
            } else {
                arena.executeSequential(commands);
            }
        } catch (MoveBlockedException e) {
            System.out.println("Execution stopped: " + e.getMessage());
        }

        if (arenaRenderer != null) {
            arenaRenderer.onAllComplete();
        }

        // Print final positions
        Map<String, Position> positions = arena.getPositions();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Position> entry : positions.entrySet()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(entry.getKey()).append(":").append(entry.getValue().x()).append(",").append(entry.getValue().y());
        }
        System.out.println(sb);
    }

    private static Direction parseDirection(String dir) {
        return switch (dir.toUpperCase()) {
            case "N" -> Direction.NORTH;
            case "S" -> Direction.SOUTH;
            case "E" -> Direction.EAST;
            case "W" -> Direction.WEST;
            default -> throw new IllegalArgumentException("Invalid direction: " + dir + " (expected N/S/E/W)");
        };
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

    /**
     * Resolves a theme by name. Falls back to {@link MonoTheme} when the terminal
     * does not support 256-color (detected via the {@code TERM} environment variable).
     *
     * @param name theme name: "modern", "minimal", "mono", or null for auto-detect
     * @return the resolved theme instance
     */
    static Theme resolveTheme(String name) {
        if (name != null) {
            return switch (name.toLowerCase()) {
                case "modern"  -> new ModernTheme();
                case "minimal" -> new MinimalTheme();
                case "mono"    -> new MonoTheme();
                default -> throw new IllegalArgumentException(
                        "Unknown theme: " + name + " (expected modern, minimal, or mono)");
            };
        }
        // Auto-detect: fall back to mono if terminal appears to lack color support
        String term = System.getenv("TERM");
        if (term == null || term.equals("dumb") || term.equals("unknown")) {
            return new MonoTheme();
        }
        return new ModernTheme();
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
