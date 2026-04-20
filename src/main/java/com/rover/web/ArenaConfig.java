package com.rover.web;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * Immutable arena configuration submitted by the browser.
 *
 * <p>{@code width} and {@code height} are nullable to support unbounded mode:
 * when both are null, the session uses an {@link com.rover.UnboundedEnvironment};
 * when both are positive, a {@link com.rover.GridEnvironment}. Partial
 * specification (only one of the two) is a validation error.</p>
 *
 * <p>{@code delayMs} is the persistent default animation pacing for SSE
 * step events (V6b). Null means "use server default" (500ms). Validated to
 * {@code [0, 5000]} by {@link ArenaConfigMapper}. Per-run overrides may be
 * supplied via the {@code POST /run} body without re-saving the config.</p>
 *
 * @param width          grid width, or {@code null} for unbounded
 * @param height         grid height, or {@code null} for unbounded
 * @param wrap           toroidal boundary mode (ignored when width/height are null)
 * @param obstacles      obstacle coordinates
 * @param conflictPolicy {@code "fail"} / {@code "skip"} / {@code "reverse"} (case-insensitive)
 * @param rovers         rover definitions (must be non-empty)
 * @param parallel       parallel (round-robin) execution mode
 * @param delayMs        SSE step pacing in ms; {@code null} → server default (500)
 */
public record ArenaConfig(
        Integer width,
        Integer height,
        boolean wrap,
        List<PositionDto> obstacles,
        String conflictPolicy,
        List<RoverSpecDto> rovers,
        boolean parallel,
        Long delayMs
) {

    /**
     * V6a-compatible 7-arg constructor: defaults {@code delayMs} to {@code null}
     * (server default 500ms applies). Lets pre-V6b call sites compile unchanged.
     */
    public ArenaConfig(Integer width, Integer height, boolean wrap,
                       List<PositionDto> obstacles, String conflictPolicy,
                       List<RoverSpecDto> rovers, boolean parallel) {
        this(width, height, wrap, obstacles, conflictPolicy, rovers, parallel, null);
    }

    /**
     * Returns true if neither width nor height is set, meaning the session
     * should use an unbounded environment.
     *
     * <p>Marked {@link JsonIgnore} so Jackson does not emit a derived
     * {@code "unbounded"} property in the serialized JSON (which would
     * then fail to round-trip back into the record).</p>
     *
     * @return true when both dimensions are null
     */
    @JsonIgnore
    public boolean isUnbounded() {
        return width == null && height == null;
    }
}
