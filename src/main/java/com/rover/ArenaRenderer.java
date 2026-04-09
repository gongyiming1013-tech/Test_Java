package com.rover;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Renders multiple rovers on a shared terminal grid.
 *
 * <p>Each rover is assigned a label (A, B, C, ...). Current positions
 * are shown as uppercase labels, trails as lowercase. Call
 * {@link #listenerFor(String)} to create a per-rover listener.</p>
 *
 * <p>Symbols:
 * <ul>
 *   <li>{@code A B C ...} — rover current position</li>
 *   <li>{@code a b c ...} — rover path trail</li>
 *   <li>{@code #} — obstacle</li>
 *   <li>{@code .} — empty cell</li>
 * </ul>
 */
public class ArenaRenderer {

    private final int width;
    private final int height;
    private final Set<Position> obstacles;
    private final Arena arena;
    private final PrintStream out;
    private final Map<String, Character> labels = new LinkedHashMap<>();
    private final Map<String, Map<Position, Character>> trails = new LinkedHashMap<>();
    private char nextLabel = 'A';

    /**
     * Creates an arena renderer with default output to System.out.
     *
     * @param width     viewport width in cells
     * @param height    viewport height in cells
     * @param obstacles obstacle positions to render
     * @param arena     the arena to query for rover positions
     */
    public ArenaRenderer(int width, int height, Set<Position> obstacles, Arena arena) {
        this(width, height, obstacles, arena, System.out);
    }

    /**
     * Creates an arena renderer with a custom output stream.
     *
     * @param width     viewport width in cells
     * @param height    viewport height in cells
     * @param obstacles obstacle positions to render
     * @param arena     the arena to query for rover positions
     * @param out       output stream
     */
    public ArenaRenderer(int width, int height, Set<Position> obstacles, Arena arena, PrintStream out) {
        this.width = width;
        this.height = height;
        this.obstacles = obstacles;
        this.arena = arena;
        this.out = out;
    }

    /**
     * Creates a {@link RoverListener} that routes events to this renderer
     * with the given rover ID context.
     *
     * @param roverId the rover's unique identifier
     * @return a listener to attach to the rover
     */
    public RoverListener listenerFor(String roverId) {
        char label = nextLabel++;
        labels.put(roverId, label);
        trails.put(roverId, new LinkedHashMap<>());

        return new RoverListener() {
            @Override
            public void onStep(RoverEvent event) {
                onRoverStep(roverId, event);
            }

            @Override
            public void onComplete(RoverState finalState) {
                // Handled at arena level via onAllComplete()
            }
        };
    }

    /**
     * Called when execution of all rovers is finished.
     * Prints final positions for all rovers.
     */
    public void onAllComplete() {
        StringBuilder sb = new StringBuilder("\nAll complete. ");
        for (Map.Entry<String, Character> entry : labels.entrySet()) {
            String id = entry.getKey();
            char label = entry.getValue();
            Rover rover = arena.getRover(id);
            Position pos = rover.getPosition();
            sb.append(String.format("%c(%s):%d,%d  ", label, id, pos.x(), pos.y()));
        }
        out.println(sb.toString().trim());
    }

    private void onRoverStep(String roverId, RoverEvent event) {
        Map<Position, Character> trail = trails.get(roverId);
        char trailChar = Character.toLowerCase(labels.get(roverId));

        if (event.stepIndex() == 0) {
            trail.put(event.previousState().position(), trailChar);
        }
        trail.put(event.newState().position(), trailChar);

        render(roverId, event);
    }

    private void render(String activeRoverId, RoverEvent event) {
        Map<String, RoverState> allStates = arena.getAllStates();
        StringBuilder sb = new StringBuilder();

        // Clear screen
        sb.append("\033[2J\033[H");

        // Top border
        sb.append("┌");
        for (int x = 0; x < width; x++) sb.append("──");
        sb.append("─┐\n");

        // Grid rows
        for (int y = height - 1; y >= 0; y--) {
            sb.append("│ ");
            for (int x = 0; x < width; x++) {
                Position pos = new Position(x, y);
                char cell = cellAt(pos, allStates);
                sb.append(cell).append(' ');
            }
            sb.append("│\n");
        }

        // Bottom border
        sb.append("└");
        for (int x = 0; x < width; x++) sb.append("──");
        sb.append("─┘\n");

        // Step info
        String actionName = event.action().getClass().getSimpleName().replace("Action", "");
        String blocked = event.blocked() ? " [BLOCKED]" : "";
        char activeLabel = labels.get(activeRoverId);
        sb.append(String.format("%c(%s) Step %d/%d: %s (%d,%d) facing %s%s",
                activeLabel, activeRoverId,
                event.stepIndex() + 1, event.totalSteps(), actionName,
                event.newState().position().x(), event.newState().position().y(),
                event.newState().direction(), blocked));

        // All rover positions summary
        sb.append("\n");
        for (Map.Entry<String, RoverState> entry : allStates.entrySet()) {
            char label = labels.getOrDefault(entry.getKey(), '?');
            Position pos = entry.getValue().position();
            sb.append(String.format("  %c(%s): %d,%d %s", label, entry.getKey(),
                    pos.x(), pos.y(), entry.getValue().direction()));
        }
        sb.append("\n");

        out.print(sb);
        out.flush();
    }

    private char cellAt(Position pos, Map<String, RoverState> allStates) {
        // Check if any rover is currently at this position
        for (Map.Entry<String, RoverState> entry : allStates.entrySet()) {
            if (entry.getValue().position().equals(pos)) {
                return labels.getOrDefault(entry.getKey(), '?');
            }
        }
        // Check obstacles
        if (obstacles.contains(pos)) {
            return '#';
        }
        // Check trails (later rover trail overwrites earlier if overlapping)
        char trailChar = '.';
        for (Map<Position, Character> trail : trails.values()) {
            if (trail.containsKey(pos)) {
                trailChar = trail.get(pos);
            }
        }
        return trailChar;
    }
}
