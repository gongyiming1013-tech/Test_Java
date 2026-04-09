package com.rover;

import org.junit.Test;

import static org.junit.Assert.*;

/** Tests for {@link GridFrame}. */
public class GridFrameTest {

    /** Use MonoTheme so assertions don't have to deal with ANSI codes. */
    private final Theme mono = new MonoTheme();

    @Test
    public void renderTop_containsBoxDrawingCorners() {
        GridFrame frame = new GridFrame(3, 3, mono);
        String top = frame.renderTop();
        assertTrue("Top should contain ┌", top.contains("┌"));
        assertTrue("Top should contain ┐", top.contains("┐"));
    }

    @Test
    public void renderTop_containsColumnNumbers() {
        GridFrame frame = new GridFrame(3, 3, mono);
        String top = frame.renderTop();
        assertTrue("Should show column 0", top.contains("0"));
        assertTrue("Should show column 1", top.contains("1"));
        assertTrue("Should show column 2", top.contains("2"));
    }

    @Test
    public void renderBottom_containsBoxDrawingCorners() {
        GridFrame frame = new GridFrame(3, 3, mono);
        String bottom = frame.renderBottom();
        assertTrue("Bottom should contain └", bottom.contains("└"));
        assertTrue("Bottom should contain ┘", bottom.contains("┘"));
    }

    @Test
    public void renderRow_containsSideBorders() {
        GridFrame frame = new GridFrame(3, 3, mono);
        String[] cells = {".", ".", "."};
        String row = frame.renderRow(1, cells);
        // Should have at least 2 │ characters (left and right border)
        long pipeCount = row.chars().filter(c -> c == '│').count();
        assertTrue("Row should have left and right border │", pipeCount >= 2);
    }

    @Test
    public void renderRow_containsRowLabel() {
        GridFrame frame = new GridFrame(3, 3, mono);
        String[] cells = {".", ".", "."};
        String row = frame.renderRow(2, cells);
        assertTrue("Row should contain y label", row.contains("2"));
    }

    @Test
    public void renderRow_containsCellContent() {
        GridFrame frame = new GridFrame(3, 3, mono);
        String[] cells = {"▲", "#", "."};
        String row = frame.renderRow(0, cells);
        assertTrue("Row should contain rover symbol", row.contains("▲"));
        assertTrue("Row should contain obstacle", row.contains("#"));
    }

    @Test
    public void renderTop_scalesWithWidth() {
        GridFrame small = new GridFrame(2, 2, mono);
        GridFrame large = new GridFrame(5, 2, mono);
        String smallTop = small.renderTop();
        String largeTop = large.renderTop();
        // Larger grid should have longer top border
        assertTrue("Wider grid should have wider border",
                largeTop.length() > smallTop.length());
    }

    @Test
    public void renderWithModernTheme_containsAnsiCodes() {
        GridFrame frame = new GridFrame(3, 3, new ModernTheme());
        String top = frame.renderTop();
        assertTrue("Modern theme should produce ANSI codes", top.contains("\033["));
    }

    @Test
    public void renderWithMonoTheme_noAnsiCodes() {
        GridFrame frame = new GridFrame(3, 3, mono);
        String top = frame.renderTop();
        assertFalse("Mono theme should not produce ANSI codes", top.contains("\033["));

        String bottom = frame.renderBottom();
        assertFalse(bottom.contains("\033["));

        String[] cells = {".", ".", "."};
        String row = frame.renderRow(0, cells);
        assertFalse(row.contains("\033["));
    }
}
