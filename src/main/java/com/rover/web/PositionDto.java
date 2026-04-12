package com.rover.web;

/**
 * JSON-serializable 2D coordinate.
 *
 * <p>Mirrors the domain {@link com.rover.Position} but is annotated-free
 * and belongs to the web DTO layer so the domain stays independent of
 * Jackson/JSON concerns.</p>
 *
 * @param x horizontal coordinate
 * @param y vertical coordinate
 */
public record PositionDto(int x, int y) {
}
