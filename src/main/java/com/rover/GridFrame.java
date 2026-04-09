package com.rover;

/**
 * Renders a Unicode box-drawing border with axis labels around the grid.
 *
 * <p>Uses box-drawing characters ({@code ┌ ─ ┐ │ └ ┘}) for a clean,
 * modern frame. Column numbers are printed along the top; row numbers
 * along the right side.</p>
 */
public class GridFrame {

    private final int width;
    private final int height;
    private final Theme theme;

    /**
     * Creates a grid frame.
     *
     * @param width  grid width in cells
     * @param height grid height in cells
     * @param theme  visual theme for border colors
     */
    public GridFrame(int width, int height, Theme theme) {
        this.width = width;
        this.height = height;
        this.theme = theme;
    }

    /**
     * Renders the top border with column numbers.
     * Format: {@code ┌──0──1──2──┐}
     *
     * @return the top border string (includes trailing newline)
     */
    public String renderTop() {
        String bc = theme.borderColor();
        String reset = resetFor(bc);
        StringBuilder sb = new StringBuilder();

        // Column numbers row
        sb.append(bc).append("    ");
        for (int x = 0; x < width; x++) {
            sb.append(String.format("%-2d", x));
        }
        sb.append(reset).append("\n");

        // Top border
        sb.append(bc).append("  ┌─");
        for (int x = 0; x < width; x++) {
            sb.append("──");
        }
        sb.append("┐").append(reset).append("\n");

        return sb.toString();
    }

    /**
     * Renders one grid row with side borders and row number.
     * Format: {@code │ · ▲ · │ 2}
     *
     * @param y     the row's y-coordinate (for label)
     * @param cells pre-rendered cell strings (length must equal width)
     * @return the row string (includes trailing newline)
     */
    public String renderRow(int y, String[] cells) {
        String bc = theme.borderColor();
        String reset = resetFor(bc);
        StringBuilder sb = new StringBuilder();

        sb.append(bc).append("  │ ").append(reset);
        for (String cell : cells) {
            sb.append(cell).append(" ");
        }
        sb.append(bc).append("│").append(reset);
        sb.append(bc).append(" ").append(y).append(reset);
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Renders the bottom border.
     * Format: {@code └──────────┘}
     *
     * @return the bottom border string (includes trailing newline)
     */
    public String renderBottom() {
        String bc = theme.borderColor();
        String reset = resetFor(bc);
        StringBuilder sb = new StringBuilder();

        sb.append(bc).append("  └─");
        for (int x = 0; x < width; x++) {
            sb.append("──");
        }
        sb.append("┘").append(reset).append("\n");

        return sb.toString();
    }

    /** Returns ANSI reset only when the style string is non-empty (has color). */
    private static String resetFor(String style) {
        return style.isEmpty() ? "" : AnsiStyle.reset();
    }
}
