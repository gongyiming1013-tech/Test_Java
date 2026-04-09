package com.rover;

import org.junit.Test;

import static org.junit.Assert.*;

/** Tests for App arena mode CLI flags. */
public class AppArenaTest {

    @Test
    public void main_arena_twoRovers_sequential() {
        App.main(new String[]{
                "--arena", "--grid", "10x10",
                "--rover", "R1:0,0,N:MMR",
                "--rover", "R2:5,5,S:MM"
        });
        // Just verify no exception — output goes to stdout
    }

    @Test
    public void main_arena_parallel() {
        App.main(new String[]{
                "--arena", "--grid", "10x10", "--parallel",
                "--rover", "R1:0,0,N:MM",
                "--rover", "R2:5,5,S:MM"
        });
    }

    @Test
    public void main_arena_withConflictPolicy() {
        App.main(new String[]{
                "--arena", "--grid", "5x5", "--on-conflict", "skip",
                "--rover", "R1:0,0,N:MMMMMM",
                "--rover", "R2:0,3,S:MM"
        });
    }

    @Test
    public void main_arena_visual() {
        App.main(new String[]{
                "--arena", "--grid", "5x5", "--visual", "--delay", "0",
                "--rover", "R1:0,0,N:MM",
                "--rover", "R2:4,4,S:MM"
        });
    }

    @Test
    public void main_arena_withObstacles() {
        App.main(new String[]{
                "--arena", "--grid", "5x5", "--obstacles", "2,2",
                "--on-conflict", "skip",
                "--rover", "R1:0,0,N:MM",
                "--rover", "R2:4,4,S:MM"
        });
    }

    @Test
    public void main_withoutArena_singleRover_unchanged() {
        // Backward compatible — no --arena flag
        App.main(new String[]{"MMM"});
    }

    @Test
    public void main_arena_eastAndWestDirections() {
        App.main(new String[]{
                "--arena", "--grid", "10x10",
                "--rover", "R1:0,0,E:MM",
                "--rover", "R2:9,9,W:MM"
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void main_arena_invalidRoverSpec_throws() {
        App.main(new String[]{
                "--arena", "--rover", "bad-spec"
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void main_arena_invalidRoverPosition_throws() {
        App.main(new String[]{
                "--arena", "--rover", "R1:0,0:MM"  // missing direction
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void main_arena_invalidDirection_throws() {
        App.main(new String[]{
                "--arena", "--rover", "R1:0,0,X:MM"
        });
    }

    @Test
    public void main_arena_failPolicy_blockedByCollision() {
        // R1 and R2 adjacent, R1 moves into R2 → blocked, caught by App
        App.main(new String[]{
                "--arena", "--grid", "5x5", "--on-conflict", "fail",
                "--rover", "R1:0,0,N:M",
                "--rover", "R2:0,1,S:M"  // R2 doesn't move because R1 executes first in sequential
        });
    }
}
