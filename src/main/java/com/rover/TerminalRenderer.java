package com.rover;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Renders the rover's movement on a terminal grid using ANSI escape codes.
 *
 * <p>Implements {@link RoverListener} to receive step-by-step updates
 * and redraws the grid after each action.</p>
 *
 * <p>Symbols:
 * <ul>
 *   <li>{@code ▲ ▼ ◄ ►} — rover current position (facing N/S/W/E)</li>
 *   <li>{@code ↑ ↓ ← →} — path trail showing direction the rover was heading</li>
 *   <li>{@code #} — obstacle</li>
 *   <li>{@code .} — empty cell</li>
 * </ul>
 */
public class TerminalRenderer implements RoverListener {

    private final int width;
    private final int height;
    private final Set<Position> obstacles;
    private final Map<Position, Direction> trail = new LinkedHashMap<>();
    private final PrintStream out;

    /**
     * Creates a renderer with default output to System.out.
     *
     * @param width     viewport width in cells
     * @param height    viewport height in cells
     * @param obstacles obstacle positions to render
     */
    public TerminalRenderer(int width, int height, Set<Position> obstacles) {
        this(width, height, obstacles, System.out);
    }

    /**
     * Creates a renderer with a custom output stream (useful for testing).
     *
     * @param width     viewport width in cells
     * @param height    viewport height in cells
     * @param obstacles obstacle positions to render
     * @param out       output stream
     */
    public TerminalRenderer(int width, int height, Set<Position> obstacles, PrintStream out) {
        this.width = width;
        this.height = height;
        this.obstacles = obstacles;
        this.out = out;
    }

    @Override
    public void onStep(RoverEvent event) {
        if (event.stepIndex() == 0) {
            trail.put(event.previousState().position(), event.previousState().direction());
        }
        // Record the direction the rover had when it arrived at / passed through this cell
        trail.put(event.newState().position(), event.newState().direction());
        render(event.newState(), event.stepIndex(), event.totalSteps(), event.action(), event.blocked());
    }

    @Override
    public void onComplete(RoverState finalState) {
        out.println("\nComplete. Final position: "
                + finalState.position().x() + ":" + finalState.position().y()
                + " facing " + finalState.direction());
    }

    private void render(RoverState state, int step, int total, Action action, boolean blocked) {
        StringBuilder sb = new StringBuilder();

        // Clear screen and move cursor to top-left
        sb.append("\033[2J\033[H");

        // Top border
        sb.append("┌");
        for (int x = 0; x < width; x++) {
            sb.append("──");
        }
        sb.append("─┐\n");

        // Grid rows (top-to-bottom = y from height-1 to 0)
        for (int y = height - 1; y >= 0; y--) {
            sb.append("│ ");
            for (int x = 0; x < width; x++) {
                Position pos = new Position(x, y);
                if (pos.equals(state.position())) {
                    sb.append(roverSymbol(state.direction()));
                } else if (obstacles.contains(pos)) {
                    sb.append('#');
                } else if (trail.containsKey(pos)) {
                    sb.append(trailSymbol(trail.get(pos)));
                } else {
                    sb.append('.');
                }
                sb.append(' ');
            }
            sb.append("│\n");
        }

        // Bottom border
        sb.append("└");
        for (int x = 0; x < width; x++) {
            sb.append("──");
        }
        sb.append("─┘\n");

        // Step info
        String actionName = action.getClass().getSimpleName().replace("Action", "");
        String status = blocked ? " [BLOCKED]" : "";
        sb.append(String.format("Step %d/%d: %s (%d,%d) facing %s%s%n",
                step + 1, total, actionName,
                state.position().x(), state.position().y(),
                state.direction(), status));

        out.print(sb);
        out.flush();
    }

    /** Rover's current position — filled arrow. */
    private char roverSymbol(Direction direction) {
        return switch (direction) {
            case NORTH -> '▲';
            case SOUTH -> '▼';
            case EAST -> '►';
            case WEST -> '◄';
        };
    }

    /** Path trail — thin arrow showing direction of travel. */
    private char trailSymbol(Direction direction) {
        return switch (direction) {
            case NORTH -> '↑';
            case SOUTH -> '↓';
            case EAST -> '→';
            case WEST -> '←';
        };
    }
}
