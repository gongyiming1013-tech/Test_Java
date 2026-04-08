package com.rover;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;

public class AppTest {

    @Test
    public void run_emptyInput_returnsOrigin() {
        assertEquals("0:0", App.run(""));
    }

    @Test
    public void run_singleMove() {
        assertEquals("0:1", App.run("M"));
    }

    @Test
    public void run_complexSequence() {
        assertEquals("2:3", App.run("MMRMMLM"));
    }

    @Test
    public void run_null_returnsOrigin() {
        assertEquals("0:0", App.run(null));
    }

    @Test(expected = InvalidActionException.class)
    public void run_invalidInput_throwsException() {
        App.run("X");
    }

    @Test
    public void main_withArgs_printsResult() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        try {
            App.main(new String[]{"M"});
            assertEquals("0:1", out.toString().trim());
        } finally {
            System.setOut(System.out);
        }
    }

    @Test
    public void main_noArgs_printsOrigin() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        try {
            App.main(new String[]{});
            assertEquals("0:0", out.toString().trim());
        } finally {
            System.setOut(System.out);
        }
    }
}
