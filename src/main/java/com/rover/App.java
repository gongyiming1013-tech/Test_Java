package com.rover;

import java.util.List;

/**
 * CLI entry point for the rover control system.
 *
 * <p>Accepts a command string as a CLI argument and prints the
 * rover's final position in {@code "x:y"} format.</p>
 */
public class App {

    /**
     * Runs the rover with the given command string.
     *
     * @param commands the command string (e.g. "MMRMMLM"), or null
     * @return the final position as "x:y"
     * @throws InvalidActionException if the commands contain invalid characters
     */
    public static String run(String commands) {
        ActionParser parser = new ActionParser();
        List<Action> actions = parser.parse(commands);

        Rover rover = new Rover();
        rover.execute(actions);

        Position pos = rover.getPosition();
        return pos.x() + ":" + pos.y();
    }

    /** CLI entry point. Reads the command string from the first argument. */
    public static void main(String[] args) {
        String commands = (args.length > 0) ? args[0] : "";
        String result = run(commands);
        System.out.println(result);
    }
}
