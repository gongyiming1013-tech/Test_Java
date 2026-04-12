package com.rover.web;

/**
 * Thrown by {@link ArenaConfigMapper} when an {@link ArenaConfig} fails validation.
 *
 * <p>Carries a machine-readable {@code code} (e.g., {@code "INVALID_GRID"},
 * {@code "UNKNOWN_DIRECTION"}, {@code "DUPLICATE_ROVER_ID"}) so the REST layer
 * can translate exceptions into consistent {@link WebError} responses.</p>
 */
public class ConfigValidationException extends IllegalArgumentException {

    private final String code;

    /**
     * Creates a validation exception.
     *
     * @param code    short uppercase error code
     * @param message human-readable description
     */
    public ConfigValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Returns the machine-readable error code.
     *
     * @return error code
     */
    public String getCode() {
        return code;
    }
}
