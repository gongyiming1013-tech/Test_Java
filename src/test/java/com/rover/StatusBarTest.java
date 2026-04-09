package com.rover;

import org.junit.Test;

import static org.junit.Assert.*;

/** Tests for {@link StatusBar}. */
public class StatusBarTest {

    private final StatusBar statusBar = new StatusBar(new MonoTheme());

    // --- progressBar ---

    @Test
    public void progressBar_atZeroPercent() {
        String bar = statusBar.progressBar(0, 10, 10);
        assertEquals("░░░░░░░░░░", bar);
    }

    @Test
    public void progressBar_atFiftyPercent() {
        String bar = statusBar.progressBar(5, 10, 10);
        assertEquals("█████░░░░░", bar);
    }

    @Test
    public void progressBar_atHundredPercent() {
        String bar = statusBar.progressBar(10, 10, 10);
        assertEquals("██████████", bar);
    }

    @Test
    public void progressBar_singleStep() {
        String bar = statusBar.progressBar(1, 1, 10);
        assertEquals("██████████", bar);
    }

    @Test
    public void progressBar_zeroTotal() {
        String bar = statusBar.progressBar(0, 0, 10);
        assertEquals("░░░░░░░░░░", bar);
    }

    @Test
    public void progressBar_variousWidths() {
        String bar5 = statusBar.progressBar(5, 10, 5);
        assertEquals(5, bar5.codePointCount(0, bar5.length()));

        String bar20 = statusBar.progressBar(5, 10, 20);
        assertEquals(20, bar20.codePointCount(0, bar20.length()));
    }

    // --- commandPreview ---

    @Test
    public void commandPreview_highlightsCurrentAction() {
        String preview = statusBar.commandPreview("MMRML", 2);
        assertEquals("M M [R] M L", preview);
    }

    @Test
    public void commandPreview_highlightsFirstAction() {
        String preview = statusBar.commandPreview("MRL", 0);
        assertEquals("[M] R L", preview);
    }

    @Test
    public void commandPreview_highlightsLastAction() {
        String preview = statusBar.commandPreview("MRL", 2);
        assertEquals("M R [L]", preview);
    }

    @Test
    public void commandPreview_singleCommand() {
        String preview = statusBar.commandPreview("M", 0);
        assertEquals("[M]", preview);
    }

    @Test
    public void commandPreview_emptyCommands() {
        assertEquals("", statusBar.commandPreview("", 0));
    }

    @Test
    public void commandPreview_nullCommands() {
        assertEquals("", statusBar.commandPreview(null, 0));
    }

    // --- render (full status bar) ---

    @Test
    public void render_containsStepInfo() {
        RoverEvent event = makeEvent(2, 5, false);
        String output = statusBar.render(event, "MMRML");
        assertTrue("Should contain step count", output.contains("Step 3/5"));
    }

    @Test
    public void render_containsProgressBar() {
        RoverEvent event = makeEvent(2, 5, false);
        String output = statusBar.render(event, "MMRML");
        // Should contain some filled and some empty blocks
        assertTrue("Should contain filled blocks", output.contains("█"));
        assertTrue("Should contain empty blocks", output.contains("░"));
    }

    @Test
    public void render_containsCommandPreview() {
        RoverEvent event = makeEvent(2, 5, false);
        String output = statusBar.render(event, "MMRML");
        assertTrue("Should contain highlighted command", output.contains("[R]"));
    }

    @Test
    public void render_containsRoverState() {
        RoverEvent event = makeEvent(2, 5, false);
        String output = statusBar.render(event, "MMRML");
        assertTrue("Should contain position", output.contains("(1,2)"));
        assertTrue("Should contain direction", output.contains("NORTH"));
    }

    @Test
    public void render_normalMove_showsMovingStatus() {
        RoverEvent event = makeEvent(0, 3, false);
        String output = statusBar.render(event, "MMR");
        assertTrue("Should show Moving status", output.contains("Moving..."));
    }

    @Test
    public void render_blockedMove_showsBlockedStatus() {
        RoverEvent event = makeEvent(0, 3, true);
        String output = statusBar.render(event, "MMR");
        assertTrue("Should show Blocked status", output.contains("Blocked"));
        assertTrue("Should contain warning symbol", output.contains("⚠"));
    }

    private RoverEvent makeEvent(int stepIndex, int totalSteps, boolean blocked) {
        RoverState prev = new RoverState(new Position(0, 0), Direction.NORTH);
        RoverState next = new RoverState(new Position(1, 2), Direction.NORTH);
        return new RoverEvent(prev, next, new MoveForwardAction(), stepIndex, totalSteps, blocked);
    }
}
