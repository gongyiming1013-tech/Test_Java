package com.rover;

/**
 * Renders the informational status panel below the grid.
 *
 * <p>Displays a progress bar, command preview with the current action
 * highlighted, rover state (position and direction), and status message
 * (e.g., "Moving..." or "⚠ Blocked").</p>
 */
public class StatusBar {

    private static final int DEFAULT_BAR_WIDTH = 10;
    private final Theme theme;

    /**
     * Creates a status bar.
     *
     * @param theme visual theme for styling
     */
    public StatusBar(Theme theme) {
        this.theme = theme;
    }

    /**
     * Renders the full status bar for one step.
     *
     * @param event           the current step event
     * @param commandSequence the full command string (e.g., "MMRMMLLM")
     * @return multi-line status bar string
     */
    public String render(RoverEvent event, String commandSequence) {
        String style = theme.statusStyle();
        String reset = resetFor(style);
        StringBuilder sb = new StringBuilder();

        // Line 1: Step N/M  ████░░░░░░  Command: M M R [M] M L M
        sb.append(style);
        sb.append(String.format("  Step %d/%d  ", event.stepIndex() + 1, event.totalSteps()));
        sb.append(progressBar(event.stepIndex() + 1, event.totalSteps(), DEFAULT_BAR_WIDTH));
        sb.append("  Command: ");
        sb.append(commandPreview(commandSequence, event.stepIndex()));
        sb.append(reset).append("\n");

        // Line 2: Rover: (x,y) facing DIRECTION     Status: Moving.../⚠ Blocked
        sb.append(style);
        Position pos = event.newState().position();
        sb.append(String.format("  Rover: (%d,%d) facing %s", pos.x(), pos.y(), event.newState().direction()));

        sb.append("     Status: ");
        if (event.blocked()) {
            String blockedStyle = theme.blockedColor();
            sb.append(resetFor(blockedStyle)).append(blockedStyle);
            sb.append("⚠ Blocked");
        } else {
            sb.append("Moving...");
        }
        sb.append(reset).append("\n");

        return sb.toString();
    }

    /** Returns ANSI reset only when the style string is non-empty (has color). */
    private static String resetFor(String style) {
        return style.isEmpty() ? "" : AnsiStyle.reset();
    }

    /**
     * Renders a progress bar.
     *
     * @param current current step (1-based)
     * @param total   total number of steps
     * @param width   bar width in characters
     * @return progress bar string (e.g., "████░░░░░░")
     */
    public String progressBar(int current, int total, int width) {
        if (total <= 0) {
            return "░".repeat(width);
        }
        int filled = (int) ((long) current * width / total);
        filled = Math.min(filled, width);
        int empty = width - filled;
        return "█".repeat(filled) + "░".repeat(empty);
    }

    /**
     * Renders the command sequence with the current action highlighted in brackets.
     *
     * @param commands     the full command string
     * @param currentIndex the zero-based index of the current action
     * @return formatted command preview (e.g., "M M R [M] M L")
     */
    public String commandPreview(String commands, int currentIndex) {
        if (commands == null || commands.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < commands.length(); i++) {
            if (i > 0) {
                sb.append(" ");
            }
            if (i == currentIndex) {
                sb.append("[").append(commands.charAt(i)).append("]");
            } else {
                sb.append(commands.charAt(i));
            }
        }
        return sb.toString();
    }
}
