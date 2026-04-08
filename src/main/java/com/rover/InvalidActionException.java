package com.rover;

/**
 * Thrown when a command string contains an unrecognised action character.
 */
public class InvalidActionException extends IllegalArgumentException {

    /**
     * @param character the invalid character
     * @param position  zero-based index within the command string
     */
    public InvalidActionException(char character, int position) {
        super("Invalid action '" + character + "' at position " + position);
    }
}
