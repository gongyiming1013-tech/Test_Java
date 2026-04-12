package com.rover.web;

import java.util.List;
import java.util.Map;

/**
 * Complete session state snapshot returned by {@code GET /api/session/{id}/state}.
 *
 * <p>Contains everything the frontend needs to render a single canvas update:
 * config (for UI form sync), current rover states, full trails for path
 * rendering, auto-fit viewport, and last-run stats.</p>
 *
 * @param sessionId session UUID
 * @param config    current arena configuration
 * @param rovers    current rover states keyed by rover ID
 * @param trails    path trails per rover, in order of visitation
 * @param viewport  visible coordinate range
 * @param stats     summary of most recent execution (zeros if no run yet)
 * @param running   whether a run is currently in progress
 */
public record SessionSnapshot(
        String sessionId,
        ArenaConfig config,
        Map<String, RoverStateDto> rovers,
        Map<String, List<PositionDto>> trails,
        ViewportDto viewport,
        RunStats stats,
        boolean running
) {
}
