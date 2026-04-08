package com.rover;

/**
 * Defines how grid edges are treated when the rover attempts to move beyond them.
 */
public enum BoundaryMode {

    /** Moving beyond the edge triggers the {@link ConflictPolicy}. */
    BOUNDED,

    /** Position wraps to the opposite edge (toroidal); no conflict triggered. */
    WRAP
}
