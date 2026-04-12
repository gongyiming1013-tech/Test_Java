package com.rover.web;

/**
 * JSON-serializable snapshot of a rover's current state.
 *
 * @param x         current x coordinate
 * @param y         current y coordinate
 * @param direction full direction name: {@code "NORTH"}, {@code "EAST"}, {@code "SOUTH"}, or {@code "WEST"}
 */
public record RoverStateDto(int x, int y, String direction) {
}
