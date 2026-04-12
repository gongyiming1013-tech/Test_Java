package com.rover.web;

/**
 * Configuration for a single rover submitted from the frontend.
 *
 * @param id        unique rover identifier (e.g., "R1")
 * @param x         starting x coordinate
 * @param y         starting y coordinate
 * @param direction starting direction: {@code "N"}, {@code "E"}, {@code "S"}, or {@code "W"} (case-insensitive)
 * @param commands  command string (e.g., {@code "MMRMM"})
 */
public record RoverSpecDto(String id, int x, int y, String direction, String commands) {
}
