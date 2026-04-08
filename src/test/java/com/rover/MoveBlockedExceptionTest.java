package com.rover;

import org.junit.Test;
import static org.junit.Assert.*;

/** Tests for {@link MoveBlockedException}. */
public class MoveBlockedExceptionTest {

    @Test
    public void constructor_setsFieldsCorrectly() {
        MoveBlockedException ex = new MoveBlockedException(new Position(3, 4), "boundary");
        assertEquals(new Position(3, 4), ex.getBlockedPosition());
        assertEquals("boundary", ex.getReason());
        assertTrue(ex.getMessage().contains("3,4"));
        assertTrue(ex.getMessage().contains("boundary"));
    }

    @Test
    public void isRuntimeException() {
        MoveBlockedException ex = new MoveBlockedException(new Position(0, 0), "obstacle");
        assertNotNull(ex);
        assertTrue(ex instanceof RuntimeException);
    }
}
