package com.rover;

import org.junit.Test;

import static org.junit.Assert.*;

/** Tests for {@link AnsiStyle}. */
public class AnsiStyleTest {

    @Test
    public void fg256_producesCorrectEscapeSequence() {
        assertEquals("\033[38;5;51m", AnsiStyle.fg256(51));
        assertEquals("\033[38;5;0m", AnsiStyle.fg256(0));
        assertEquals("\033[38;5;255m", AnsiStyle.fg256(255));
    }

    @Test
    public void bg256_producesCorrectEscapeSequence() {
        assertEquals("\033[48;5;196m", AnsiStyle.bg256(196));
        assertEquals("\033[48;5;0m", AnsiStyle.bg256(0));
        assertEquals("\033[48;5;255m", AnsiStyle.bg256(255));
    }

    @Test
    public void bold_producesCorrectEscapeSequence() {
        assertEquals("\033[1m", AnsiStyle.bold());
    }

    @Test
    public void dim_producesCorrectEscapeSequence() {
        assertEquals("\033[2m", AnsiStyle.dim());
    }

    @Test
    public void reset_producesCorrectEscapeSequence() {
        assertEquals("\033[0m", AnsiStyle.reset());
    }

    @Test
    public void cursorHome_producesCorrectEscapeSequence() {
        assertEquals("\033[H", AnsiStyle.cursorHome());
    }

    @Test
    public void hideCursor_producesCorrectEscapeSequence() {
        assertEquals("\033[?25l", AnsiStyle.hideCursor());
    }

    @Test
    public void showCursor_producesCorrectEscapeSequence() {
        assertEquals("\033[?25h", AnsiStyle.showCursor());
    }
}
