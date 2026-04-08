package com.rover;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

/** Tests for {@link GridEnvironment}. */
public class GridEnvironmentTest {

    // --- Construction validation ---

    @Test(expected = IllegalArgumentException.class)
    public void constructor_zeroWidth_throws() {
        new GridEnvironment(0, 5, Set.of(), BoundaryMode.BOUNDED);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_negativeHeight_throws() {
        new GridEnvironment(5, -1, Set.of(), BoundaryMode.BOUNDED);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_obstacleOutsideGrid_throws() {
        new GridEnvironment(5, 5, Set.of(new Position(5, 0)), BoundaryMode.BOUNDED);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullObstacles_throws() {
        new GridEnvironment(5, 5, null, BoundaryMode.BOUNDED);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_obstacleNegativeX_throws() {
        new GridEnvironment(5, 5, Set.of(new Position(-1, 0)), BoundaryMode.BOUNDED);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_obstacleNegativeY_throws() {
        new GridEnvironment(5, 5, Set.of(new Position(0, -1)), BoundaryMode.BOUNDED);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_obstacleExceedsHeight_throws() {
        new GridEnvironment(5, 5, Set.of(new Position(0, 5)), BoundaryMode.BOUNDED);
    }

    // --- BOUNDED mode: boundary checks ---

    @Test
    public void bounded_interiorMove_notBlocked() {
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.BOUNDED);
        MoveResult result = env.validate(new Position(2, 2), new Position(2, 3));
        assertEquals(new Position(2, 3), result.position());
        assertFalse(result.blocked());
    }

    @Test
    public void bounded_moveNorthAtTopEdge_blocked() {
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.BOUNDED);
        MoveResult result = env.validate(new Position(2, 4), new Position(2, 5));
        assertEquals(new Position(2, 4), result.position());
        assertTrue(result.blocked());
    }

    @Test
    public void bounded_moveEastAtRightEdge_blocked() {
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.BOUNDED);
        MoveResult result = env.validate(new Position(4, 2), new Position(5, 2));
        assertEquals(new Position(4, 2), result.position());
        assertTrue(result.blocked());
    }

    @Test
    public void bounded_moveSouthAtBottomEdge_blocked() {
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.BOUNDED);
        MoveResult result = env.validate(new Position(2, 0), new Position(2, -1));
        assertEquals(new Position(2, 0), result.position());
        assertTrue(result.blocked());
    }

    @Test
    public void bounded_moveWestAtLeftEdge_blocked() {
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.BOUNDED);
        MoveResult result = env.validate(new Position(0, 2), new Position(-1, 2));
        assertEquals(new Position(0, 2), result.position());
        assertTrue(result.blocked());
    }

    @Test
    public void bounded_moveAtCorner_blocked() {
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.BOUNDED);
        // At top-right corner, moving east
        MoveResult result = env.validate(new Position(4, 4), new Position(5, 4));
        assertTrue(result.blocked());
    }

    // --- WRAP mode: toroidal wrapping ---

    @Test
    public void wrap_moveNorthBeyondTop_wrapsToBottom() {
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.WRAP);
        MoveResult result = env.validate(new Position(2, 4), new Position(2, 5));
        assertEquals(new Position(2, 0), result.position());
        assertFalse(result.blocked());
    }

    @Test
    public void wrap_moveEastBeyondRight_wrapsToLeft() {
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.WRAP);
        MoveResult result = env.validate(new Position(4, 2), new Position(5, 2));
        assertEquals(new Position(0, 2), result.position());
        assertFalse(result.blocked());
    }

    @Test
    public void wrap_moveSouthBeyondBottom_wrapsToTop() {
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.WRAP);
        MoveResult result = env.validate(new Position(2, 0), new Position(2, -1));
        assertEquals(new Position(2, 4), result.position());
        assertFalse(result.blocked());
    }

    @Test
    public void wrap_moveWestBeyondLeft_wrapsToRight() {
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.WRAP);
        MoveResult result = env.validate(new Position(0, 2), new Position(-1, 2));
        assertEquals(new Position(4, 2), result.position());
        assertFalse(result.blocked());
    }

    @Test
    public void wrap_interiorMove_noWrapping() {
        Environment env = new GridEnvironment(5, 5, Set.of(), BoundaryMode.WRAP);
        MoveResult result = env.validate(new Position(1, 1), new Position(1, 2));
        assertEquals(new Position(1, 2), result.position());
        assertFalse(result.blocked());
    }

    // --- Obstacle checks ---

    @Test
    public void obstacle_moveIntoObstacle_blocked() {
        Environment env = new GridEnvironment(5, 5, Set.of(new Position(2, 3)), BoundaryMode.BOUNDED);
        MoveResult result = env.validate(new Position(2, 2), new Position(2, 3));
        assertEquals(new Position(2, 2), result.position());
        assertTrue(result.blocked());
    }

    @Test
    public void obstacle_adjacentButNotInPath_notBlocked() {
        Environment env = new GridEnvironment(5, 5, Set.of(new Position(3, 2)), BoundaryMode.BOUNDED);
        MoveResult result = env.validate(new Position(2, 2), new Position(2, 3));
        assertEquals(new Position(2, 3), result.position());
        assertFalse(result.blocked());
    }

    @Test
    public void obstacle_multipleObstacles_blockedByAny() {
        Set<Position> obstacles = Set.of(new Position(1, 1), new Position(2, 2), new Position(3, 3));
        Environment env = new GridEnvironment(5, 5, obstacles, BoundaryMode.BOUNDED);
        assertTrue(env.validate(new Position(1, 0), new Position(1, 1)).blocked());
        assertTrue(env.validate(new Position(2, 1), new Position(2, 2)).blocked());
        assertFalse(env.validate(new Position(0, 0), new Position(0, 1)).blocked());
    }

    @Test
    public void wrap_wrappedPositionIsObstacle_blocked() {
        // Wrapping north from (2,4) → (2,0), but (2,0) is an obstacle
        Environment env = new GridEnvironment(5, 5, Set.of(new Position(2, 0)), BoundaryMode.WRAP);
        MoveResult result = env.validate(new Position(2, 4), new Position(2, 5));
        assertEquals(new Position(2, 4), result.position());
        assertTrue(result.blocked());
    }

    // --- Getters ---

    @Test
    public void getters_returnCorrectValues() {
        Set<Position> obstacles = Set.of(new Position(1, 1));
        GridEnvironment env = new GridEnvironment(10, 8, obstacles, BoundaryMode.WRAP);
        assertEquals(10, env.getWidth());
        assertEquals(8, env.getHeight());
        assertEquals(obstacles, env.getObstacles());
        assertEquals(BoundaryMode.WRAP, env.getBoundaryMode());
    }
}
