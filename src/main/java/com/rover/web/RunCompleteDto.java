package com.rover.web;

import java.util.Map;

/**
 * JSON-serializable payload of the SSE {@code complete} event emitted at the
 * end of every run (success or {@link com.rover.MoveBlockedException}).
 *
 * @param runId       UUID of this specific run (distinct per {@code POST /run})
 * @param runCount    cumulative run count for the session (1-based)
 * @param stats       cumulative {@link RunStats} after this run
 * @param finalStates final rover states keyed by rover ID
 */
public record RunCompleteDto(
        String runId,
        int runCount,
        RunStats stats,
        Map<String, RoverStateDto> finalStates
) {
}
