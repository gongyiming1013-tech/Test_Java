package com.rover.web;

/**
 * Summary metrics for the most recent execution of a session.
 * Displayed in the UI status bar.
 *
 * @param totalSteps    total actions executed across all rovers
 * @param blockedCount  number of blocked moves across all rovers
 * @param durationMs    wall-clock execution time in milliseconds
 * @param roverCount    number of rovers in the session
 * @param obstacleCount number of obstacles in the session
 */
public record RunStats(
        int totalSteps,
        int blockedCount,
        long durationMs,
        int roverCount,
        int obstacleCount
) {

    /** Zero-valued stats used when no run has happened yet. */
    public static RunStats empty() {
        return new RunStats(0, 0, 0L, 0, 0);
    }
}
