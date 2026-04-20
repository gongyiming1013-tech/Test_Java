package com.rover.web;

import org.junit.Test;

import static org.junit.Assert.*;

/** Contract tests for {@link ArenaConfigMapper#validateAndDefaultDelay(Long)}. */
public class ArenaConfigMapperDelayTest {

    @Test
    public void nullDelay_resolvesToServerDefault() {
        assertEquals(ArenaConfigMapper.DEFAULT_DELAY_MS,
                ArenaConfigMapper.validateAndDefaultDelay(null));
    }

    @Test
    public void defaultIs500ms() {
        assertEquals("V6b spec: default 500ms", 500L, ArenaConfigMapper.DEFAULT_DELAY_MS);
    }

    @Test
    public void zeroDelay_accepted() {
        assertEquals(0L, ArenaConfigMapper.validateAndDefaultDelay(0L));
    }

    @Test
    public void positiveValueWithinRange_returnedAsIs() {
        assertEquals(500L, ArenaConfigMapper.validateAndDefaultDelay(500L));
        assertEquals(2500L, ArenaConfigMapper.validateAndDefaultDelay(2500L));
    }

    @Test
    public void maxBoundary_inclusive() {
        assertEquals(ArenaConfigMapper.MAX_DELAY_MS,
                ArenaConfigMapper.validateAndDefaultDelay(ArenaConfigMapper.MAX_DELAY_MS));
        assertEquals(5000L, ArenaConfigMapper.MAX_DELAY_MS);
    }

    @Test
    public void negativeDelay_throwsInvalidDelay() {
        try {
            ArenaConfigMapper.validateAndDefaultDelay(-1L);
            fail("expected ConfigValidationException");
        } catch (ConfigValidationException e) {
            assertEquals("INVALID_DELAY", e.getCode());
        }
    }

    @Test
    public void delayAboveMax_throwsInvalidDelay() {
        try {
            ArenaConfigMapper.validateAndDefaultDelay(ArenaConfigMapper.MAX_DELAY_MS + 1);
            fail("expected ConfigValidationException");
        } catch (ConfigValidationException e) {
            assertEquals("INVALID_DELAY", e.getCode());
        }
    }
}
