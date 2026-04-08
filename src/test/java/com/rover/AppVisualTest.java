package com.rover;

import org.junit.Test;

/** Tests for App visual mode CLI flags. */
public class AppVisualTest {

    @Test
    public void main_visualFlag_doesNotThrow() {
        // Visual mode with zero delay for fast test execution
        App.main(new String[]{"--visual", "--delay", "0", "MRM"});
    }

    @Test
    public void main_visualWithGrid_doesNotThrow() {
        App.main(new String[]{"--grid", "5x5", "--visual", "--delay", "0", "MMRM"});
    }

    @Test
    public void main_visualWithGridAndObstacles_doesNotThrow() {
        App.main(new String[]{"--grid", "5x5", "--obstacles", "1,1", "--on-conflict", "skip",
                "--visual", "--delay", "0", "MMRM"});
    }

    @Test
    public void main_visualWithWrap_doesNotThrow() {
        App.main(new String[]{"--grid", "3x3", "--wrap", "--visual", "--delay", "0", "MMMMM"});
    }

    @Test
    public void main_visualWithFail_blocked_doesNotCrash() {
        // FAIL policy hits wall — App catches MoveBlockedException in visual mode
        App.main(new String[]{"--grid", "3x3", "--on-conflict", "fail",
                "--visual", "--delay", "0", "MMMMM"});
    }

    @Test
    public void main_withoutVisual_normalOutput() {
        // Without --visual, behaves as before
        App.main(new String[]{"MMM"});
    }
}
