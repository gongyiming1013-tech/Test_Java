package com.rover;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Tests for App with V2 environment flags. */
public class AppV2Test {

    // --- No constraints (backward compatibility) ---

    @Test
    public void run_noConstraints_v1Behavior() {
        assertEquals("2:3", App.run("MMRMMLM"));
    }

    // --- Grid with FAIL ---

    @Test(expected = MoveBlockedException.class)
    public void run_gridFail_moveBeyondBoundary_throws() {
        App.run("MMMMMM", "5x5", false, null, ConflictPolicy.FAIL);
    }

    @Test
    public void run_gridFail_withinBounds_works() {
        assertEquals("0:3", App.run("MMM", "5x5", false, null, ConflictPolicy.FAIL));
    }

    // --- Grid with SKIP ---

    @Test
    public void run_gridSkip_blockedMovesSkipped() {
        // 5x5 grid, MMMMMM (6 moves north) → hits wall at y=5, rest skipped → y=4
        assertEquals("0:4", App.run("MMMMMM", "5x5", false, null, ConflictPolicy.SKIP));
    }

    // --- Grid with REVERSE ---

    @Test
    public void run_gridReverse_bouncesBack() {
        // 5x5 grid: MMMMMM — 5th move blocked → reverse south, 6th move south to y=3
        assertEquals("0:3", App.run("MMMMMM", "5x5", false, null, ConflictPolicy.REVERSE));
    }

    // --- Wrap mode ---

    @Test
    public void run_gridWrap_wrapsAround() {
        // 5x5 wrap grid: MMMMM → y goes 1,2,3,4,0 → ends at (0,0)
        assertEquals("0:0", App.run("MMMMM", "5x5", true, null, ConflictPolicy.FAIL));
    }

    // --- Obstacles ---

    @Test
    public void run_obstacles_skip() {
        // 5x5 grid, obstacle at (0,2), skip policy: MMM → M(0,1), M blocked at (0,2) skip, M blocked again skip → (0,1)
        assertEquals("0:1", App.run("MMM", "5x5", false, "0,2", ConflictPolicy.SKIP));
    }

    @Test(expected = MoveBlockedException.class)
    public void run_obstacles_fail() {
        // 5x5 grid, obstacle at (0,1), fail policy
        App.run("M", "5x5", false, "0,1", ConflictPolicy.FAIL);
    }

    // --- Invalid input ---

    @Test(expected = IllegalArgumentException.class)
    public void run_invalidGridFormat_throws() {
        App.run("M", "invalid", false, null, ConflictPolicy.FAIL);
    }

    @Test(expected = IllegalArgumentException.class)
    public void run_invalidObstacleFormat_throws() {
        App.run("M", "5x5", false, "bad", ConflictPolicy.FAIL);
    }

    // --- CLI main() flag parsing ---

    @Test
    public void main_gridFlag() {
        // Just verify it doesn't throw — output goes to stdout
        App.main(new String[]{"--grid", "5x5", "MMM"});
    }

    @Test
    public void main_wrapFlag() {
        App.main(new String[]{"--grid", "5x5", "--wrap", "MMMMM"});
    }

    @Test
    public void main_obstaclesFlag() {
        App.main(new String[]{"--grid", "5x5", "--obstacles", "1,1;2,2", "--on-conflict", "skip", "M"});
    }

    @Test
    public void main_onConflictFlag() {
        App.main(new String[]{"--grid", "5x5", "--on-conflict", "reverse", "MMMMMM"});
    }

    @Test
    public void main_noArgs() {
        App.main(new String[]{});
    }
}
