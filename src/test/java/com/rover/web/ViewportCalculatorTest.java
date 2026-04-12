package com.rover.web;

import com.rover.Position;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** Contract tests for {@link ViewportCalculator}. */
public class ViewportCalculatorTest {

    // --- forBoundedGrid ---

    @Test
    public void forBoundedGrid_10x10_matchesGridExactly() {
        ViewportDto vp = ViewportCalculator.forBoundedGrid(10, 10);
        assertEquals(0, vp.xMin());
        assertEquals(0, vp.yMin());
        assertEquals(9, vp.xMax());
        assertEquals(9, vp.yMax());
        assertEquals(10, vp.width());
        assertEquals(10, vp.height());
    }

    @Test
    public void forBoundedGrid_rectangular_5x15() {
        ViewportDto vp = ViewportCalculator.forBoundedGrid(5, 15);
        assertEquals(0, vp.xMin());
        assertEquals(0, vp.yMin());
        assertEquals(4, vp.xMax());
        assertEquals(14, vp.yMax());
        assertEquals(5, vp.width());
        assertEquals(15, vp.height());
    }

    @Test
    public void forBoundedGrid_1x1() {
        ViewportDto vp = ViewportCalculator.forBoundedGrid(1, 1);
        assertEquals(0, vp.xMin());
        assertEquals(0, vp.yMin());
        assertEquals(0, vp.xMax());
        assertEquals(0, vp.yMax());
    }

    // --- autoFit ---

    @Test
    public void autoFit_empty_returnsMinimumWindowCenteredOnOrigin() {
        ViewportDto vp = ViewportCalculator.autoFit(Set.of());
        // Minimum 10x10, centered on origin → x: -5..4, y: -5..4
        assertTrue("viewport width should be >= 10", vp.width() >= 10);
        assertTrue("viewport height should be >= 10", vp.height() >= 10);
        assertTrue("origin must be inside viewport", vp.xMin() <= 0 && vp.xMax() >= 0);
        assertTrue("origin must be inside viewport", vp.yMin() <= 0 && vp.yMax() >= 0);
    }

    @Test
    public void autoFit_singlePoint_atOrigin_returnsMinimumWindow() {
        ViewportDto vp = ViewportCalculator.autoFit(List.of(new Position(0, 0)));
        assertTrue(vp.width() >= 10);
        assertTrue(vp.height() >= 10);
        assertTrue(vp.xMin() <= 0 && vp.xMax() >= 0);
    }

    @Test
    public void autoFit_singlePointFarFromOrigin_stillIncludesOrigin() {
        ViewportDto vp = ViewportCalculator.autoFit(List.of(new Position(20, 20)));
        // Origin must always be in the viewport
        assertTrue("origin should be included", vp.xMin() <= 0);
        assertTrue("origin should be included", vp.yMin() <= 0);
        // Point should be included
        assertTrue("point should be included", vp.xMax() >= 20);
        assertTrue("point should be included", vp.yMax() >= 20);
    }

    @Test
    public void autoFit_multiplePoints_coversBoundingBoxWithPadding() {
        ViewportDto vp = ViewportCalculator.autoFit(List.of(
                new Position(3, 3),
                new Position(5, 7),
                new Position(1, 2)
        ));
        // bbox is x:[1..5], y:[2..7], origin included → x:[0..5], y:[0..7]
        // padding 2 each side → x:[-2..7], y:[-2..9]
        // but minimum 10x10, so may expand further
        assertTrue("must include origin", vp.xMin() <= 0 && vp.yMin() <= 0);
        assertTrue("must include (5,7)", vp.xMax() >= 5 && vp.yMax() >= 7);
        // should have padding
        assertTrue("should have x padding", vp.xMin() <= -1);
        assertTrue("should have y padding", vp.yMin() <= -1);
    }

    @Test
    public void autoFit_negativeCoordinates() {
        ViewportDto vp = ViewportCalculator.autoFit(List.of(
                new Position(-5, -5),
                new Position(-3, -2)
        ));
        assertTrue("should include origin", vp.xMax() >= 0 && vp.yMax() >= 0);
        assertTrue("should include far negative point", vp.xMin() <= -5 && vp.yMin() <= -5);
    }

    @Test
    public void autoFit_widelySeparatedPoints_noMinimumPadding() {
        ViewportDto vp = ViewportCalculator.autoFit(List.of(
                new Position(0, 0),
                new Position(100, 100)
        ));
        // When the natural bbox is much larger than min, minimum doesn't kick in
        assertTrue(vp.width() >= 100);
        assertTrue(vp.height() >= 100);
    }
}
