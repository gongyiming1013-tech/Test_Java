package com.rover.web;

/**
 * Uniform error response envelope returned by the REST layer on failures.
 *
 * @param code    machine-readable error code (e.g., {@code "INVALID_GRID"},
 *                {@code "SESSION_NOT_FOUND"}, {@code "CONCURRENT_RUN"})
 * @param message human-readable error message
 */
public record WebError(String code, String message) {
}
