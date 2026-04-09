package com.rover;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders multiple rovers on a shared terminal grid with theme support.
 *
 * <p>Each rover is assigned a label (A, B, C, ...) and a distinct color
 * from the active {@link Theme}. Current positions use directional symbols;
 * path trails use directional arrows color-coded per rover. Grid framing
 * and status display are delegated to {@link GridFrame} and {@link StatusBar}.</p>
 *
 * <p>V5c: flicker-free cursor-home rendering, per-rover color coding,
 * themed obstacles/borders, status dashboard.</p>
 */
public class ArenaRenderer {

    private final int width;
    private final int height;
    private final Set<Position> obstacles;
    private final Arena arena;
    private final PrintStream out;
    private final Theme theme;
    private final GridFrame gridFrame;
    private final Map<String, Character> labels = new LinkedHashMap<>();
    private final Map<String, Integer> roverIndices = new LinkedHashMap<>();
    private final Map<String, Map<Position, Direction>> trails = new LinkedHashMap<>();
    private char nextLabel = 'A';
    private int nextIndex = 0;
    private boolean started = false;

    /**
     * Creates an arena renderer with default output to System.out and MonoTheme.
     *
     * @param width     viewport width in cells
     * @param height    viewport height in cells
     * @param obstacles obstacle positions to render
     * @param arena     the arena to query for rover positions
     */
    public ArenaRenderer(int width, int height, Set<Position> obstacles, Arena arena) {
        this(width, height, obstacles, arena, System.out, new MonoTheme());
    }

    /**
     * Creates an arena renderer with a custom output stream and MonoTheme.
     *
     * @param width     viewport width in cells
     * @param height    viewport height in cells
     * @param obstacles obstacle positions to render
     * @param arena     the arena to query for rover positions
     * @param out       output stream
     */
    public ArenaRenderer(int width, int height, Set<Position> obstacles, Arena arena, PrintStream out) {
        this(width, height, obstacles, arena, out, new MonoTheme());
    }

    /**
     * Creates a fully configured V5c arena renderer.
     *
     * @param width     viewport width in cells
     * @param height    viewport height in cells
     * @param obstacles obstacle positions to render
     * @param arena     the arena to query for rover positions
     * @param out       output stream
     * @param theme     visual theme for colors and symbols
     */
    public ArenaRenderer(int width, int height, Set<Position> obstacles,
                         Arena arena, PrintStream out, Theme theme) {
        this.width = width;
        this.height = height;
        this.obstacles = obstacles;
        this.arena = arena;
        this.out = out;
        this.theme = theme;
        this.gridFrame = new GridFrame(width, height, theme);
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
        int index = nextIndex++;
        labels.put(roverId, label);
        roverIndices.put(roverId, index);
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
        out.print(AnsiStyle.showCursor());
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
        Map<Position, Direction> trail = trails.get(roverId);

        if (event.stepIndex() == 0) {
            trail.put(event.previousState().position(), event.previousState().direction());
        }
        trail.put(event.newState().position(), event.newState().direction());

        render(roverId, event);
    }

    private void render(String activeRoverId, RoverEvent event) {
        Map<String, RoverState> allStates = arena.getAllStates();
        StringBuilder sb = new StringBuilder();

        // Cursor control
        if (!started) {
            sb.append(AnsiStyle.hideCursor());
            started = true;
        }
        sb.append(AnsiStyle.cursorHome());

        // Grid frame top
        sb.append(gridFrame.renderTop());

        // Grid rows
        String reset = AnsiStyle.reset();
        for (int y = height - 1; y >= 0; y--) {
            String[] cells = new String[width];
            for (int x = 0; x < width; x++) {
                cells[x] = cellAt(new Position(x, y), allStates, reset);
            }
            sb.append(gridFrame.renderRow(y, cells));
        }

        // Grid frame bottom
        sb.append(gridFrame.renderBottom());

        // Step info for active rover
        String style = theme.statusStyle();
        String resetStyle = style.isEmpty() ? "" : reset;
        char activeLabel = labels.get(activeRoverId);
        int activeIndex = roverIndices.get(activeRoverId);
        String roverColor = theme.roverColor(activeIndex);
        String resetColor = roverColor.isEmpty() ? "" : reset;

        sb.append(style);
        sb.append("  ").append(resetStyle);
        sb.append(roverColor).append(activeLabel).append("(").append(activeRoverId).append(")").append(resetColor);
        sb.append(style);
        String actionName = event.action().getClass().getSimpleName().replace("Action", "");
        String blocked = event.blocked() ? " ⚠ Blocked" : "";
        sb.append(String.format(" Step %d/%d: %s (%d,%d) facing %s%s",
                event.stepIndex() + 1, event.totalSteps(), actionName,
                event.newState().position().x(), event.newState().position().y(),
                event.newState().direction(), blocked));
        sb.append(resetStyle).append("\n");

        // All rover positions summary
        for (Map.Entry<String, RoverState> entry : allStates.entrySet()) {
            String id = entry.getKey();
            char label = labels.getOrDefault(id, '?');
            int idx = roverIndices.getOrDefault(id, 0);
            Position pos = entry.getValue().position();
            String rc = theme.roverColor(idx);
            String rcReset = rc.isEmpty() ? "" : reset;

            sb.append(style).append("  ").append(resetStyle);
            sb.append(rc).append(label).append("(").append(id).append(")").append(rcReset);
            sb.append(style);
            sb.append(String.format(": %d,%d %s", pos.x(), pos.y(), entry.getValue().direction()));
            sb.append(resetStyle);
        }
        sb.append("\n");

        out.print(sb);
        out.flush();
    }

    private String cellAt(Position pos, Map<String, RoverState> allStates, String reset) {
        // Check if any rover is at this position
        for (Map.Entry<String, RoverState> entry : allStates.entrySet()) {
            if (entry.getValue().position().equals(pos)) {
                String id = entry.getKey();
                char label = labels.getOrDefault(id, '?');
                int index = roverIndices.getOrDefault(id, 0);
                String color = theme.roverColor(index);
                String symbol = theme.roverSymbol(entry.getValue().direction());
                return colorWrap(color, String.valueOf(label), reset);
            }
        }
        // Check obstacles
        if (obstacles.contains(pos)) {
            return colorWrap(theme.obstacleColor(), theme.obstacleSymbol(), reset);
        }
        // Check trails — later rover trail overwrites earlier if overlapping
        for (Map.Entry<String, Map<Position, Direction>> trailEntry : trails.entrySet()) {
            Map<Position, Direction> trail = trailEntry.getValue();
            if (trail.containsKey(pos)) {
                String id = trailEntry.getKey();
                int index = roverIndices.getOrDefault(id, 0);
                String color = theme.pathColor(0, theme.gradientWindow());
                char trailLabel = Character.toLowerCase(labels.getOrDefault(id, '?'));
                return colorWrap(color, String.valueOf(trailLabel), reset);
            }
        }
        return theme.emptySymbol();
    }

    /** Wraps content with ANSI color + reset, or returns bare content if color is empty. */
    private static String colorWrap(String color, String content, String reset) {
        if (color.isEmpty()) {
            return content;
        }
        return color + content + reset;
    }
}
