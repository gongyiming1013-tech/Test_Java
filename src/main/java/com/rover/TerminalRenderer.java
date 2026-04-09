package com.rover;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders the rover's movement on a terminal grid.
 *
 * <p>Implements {@link RoverListener} to receive step-by-step updates
 * and redraws the grid after each action. Delegates visual presentation
 * to a {@link Theme}, grid framing to {@link GridFrame}, and status
 * display to {@link StatusBar}.</p>
 *
 * <p>V5c enhancements: flicker-free cursor-home rendering, gradient
 * path trail coloring, blocked-move color flash, theme-driven
 * symbols/colors, status dashboard with progress bar and command preview.</p>
 */
public class TerminalRenderer implements RoverListener {

    private final int width;
    private final int height;
    private final Set<Position> obstacles;
    private final Map<Position, Direction> trail = new LinkedHashMap<>();
    private final List<Position> trailOrder = new ArrayList<>();
    private final PrintStream out;
    private final Theme theme;
    private final GridFrame gridFrame;
    private final StatusBar statusBar;
    private final String commandSequence;
    private boolean started = false;

    /**
     * Creates a renderer with default output to System.out and MonoTheme.
     *
     * @param width     viewport width in cells
     * @param height    viewport height in cells
     * @param obstacles obstacle positions to render
     */
    public TerminalRenderer(int width, int height, Set<Position> obstacles) {
        this(width, height, obstacles, System.out, new MonoTheme(), "");
    }

    /**
     * Creates a renderer with a custom output stream and MonoTheme.
     *
     * @param width     viewport width in cells
     * @param height    viewport height in cells
     * @param obstacles obstacle positions to render
     * @param out       output stream
     */
    public TerminalRenderer(int width, int height, Set<Position> obstacles, PrintStream out) {
        this(width, height, obstacles, out, new MonoTheme(), "");
    }

    /**
     * Creates a fully configured V5c renderer.
     *
     * @param width           viewport width in cells
     * @param height          viewport height in cells
     * @param obstacles       obstacle positions to render
     * @param out             output stream
     * @param theme           visual theme for colors and symbols
     * @param commandSequence the full command string for status bar preview
     */
    public TerminalRenderer(int width, int height, Set<Position> obstacles,
                            PrintStream out, Theme theme, String commandSequence) {
        this.width = width;
        this.height = height;
        this.obstacles = obstacles;
        this.out = out;
        this.theme = theme;
        this.gridFrame = new GridFrame(width, height, theme);
        this.statusBar = new StatusBar(theme);
        this.commandSequence = commandSequence != null ? commandSequence : "";
    }

    @Override
    public void onStep(RoverEvent event) {
        if (event.stepIndex() == 0) {
            addTrail(event.previousState().position(), event.previousState().direction());
        }
        addTrail(event.newState().position(), event.newState().direction());
        render(event);
    }

    @Override
    public void onComplete(RoverState finalState) {
        out.print(AnsiStyle.showCursor());
        out.println("\nComplete. Final position: "
                + finalState.position().x() + ":" + finalState.position().y()
                + " facing " + finalState.direction());
    }

    private void addTrail(Position pos, Direction direction) {
        trail.put(pos, direction);
        trailOrder.remove(pos);
        trailOrder.add(pos);
    }

    private void render(RoverEvent event) {
        StringBuilder sb = new StringBuilder();

        // Cursor control: hide cursor on first frame, then cursor-home (no clear screen)
        if (!started) {
            sb.append(AnsiStyle.hideCursor());
            started = true;
        }
        sb.append(AnsiStyle.cursorHome());

        // Grid frame top (column numbers + top border)
        sb.append(gridFrame.renderTop());

        // Grid rows (top-to-bottom = y from height-1 to 0)
        int totalTrail = trailOrder.size();
        int gradientWindow = theme.gradientWindow();

        for (int y = height - 1; y >= 0; y--) {
            String[] cells = new String[width];
            for (int x = 0; x < width; x++) {
                cells[x] = renderCell(new Position(x, y), event.newState(),
                        event.blocked(), totalTrail, gradientWindow);
            }
            sb.append(gridFrame.renderRow(y, cells));
        }

        // Grid frame bottom
        sb.append(gridFrame.renderBottom());

        // Status bar
        sb.append(statusBar.render(event, commandSequence));

        out.print(sb);
        out.flush();
    }

    private String renderCell(Position pos, RoverState state, boolean blocked,
                              int totalTrail, int gradientWindow) {
        if (pos.equals(state.position())) {
            String color = blocked ? theme.blockedColor() : theme.roverColor(0);
            return colorWrap(color, theme.roverSymbol(state.direction()));
        } else if (obstacles.contains(pos)) {
            return colorWrap(theme.obstacleColor(), theme.obstacleSymbol());
        } else if (trail.containsKey(pos)) {
            int index = trailOrder.indexOf(pos);
            int age = totalTrail - 1 - index;
            String color = theme.pathColor(Math.min(age, gradientWindow), gradientWindow);
            return colorWrap(color, trailSymbol(trail.get(pos)));
        } else {
            return theme.emptySymbol();
        }
    }

    /** Wraps content with ANSI color + reset, or returns bare content if color is empty. */
    private static String colorWrap(String color, String content) {
        if (color.isEmpty()) {
            return content;
        }
        return color + content + AnsiStyle.reset();
    }

    /** Path trail — thin arrow showing direction of travel. */
    private String trailSymbol(Direction direction) {
        return switch (direction) {
            case NORTH -> "↑";
            case SOUTH -> "↓";
            case EAST  -> "→";
            case WEST  -> "←";
        };
    }
}
