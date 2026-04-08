package com.rover;

import org.junit.Test;
import static org.junit.Assert.*;

/** Tests for {@link UnboundedEnvironment}. */
public class UnboundedEnvironmentTest {

    private final Environment env = new UnboundedEnvironment();

    @Test
    public void validate_alwaysReturnsProposedPosition() {
        MoveResult result = env.validate(new Position(0, 0), new Position(0, 1));
        assertEquals(new Position(0, 1), result.position());
        assertFalse(result.blocked());
    }

    @Test
    public void validate_negativeCoordinates_notBlocked() {
        MoveResult result = env.validate(new Position(0, 0), new Position(-1, -1));
        assertEquals(new Position(-1, -1), result.position());
        assertFalse(result.blocked());
    }

    @Test
    public void validate_largeCoordinates_notBlocked() {
        MoveResult result = env.validate(new Position(0, 0), new Position(999999, 999999));
        assertEquals(new Position(999999, 999999), result.position());
        assertFalse(result.blocked());
    }
}
