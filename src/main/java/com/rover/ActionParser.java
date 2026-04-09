package com.rover;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts a command string into an ordered list of {@link Action} objects.
 *
 * <p>Uses a character → action registry so new actions can be added via
 * {@link #register(char, Action)} without modifying this class.</p>
 */
public class ActionParser {

    private final Map<Character, Action> registry = new ConcurrentHashMap<>();

    /** Creates a parser pre-loaded with the default actions (L, R, M, B, S, U, Z, Y). */
    public ActionParser() {
        registry.put('L', new TurnLeftAction());
        registry.put('R', new TurnRightAction());
        registry.put('M', new MoveForwardAction());
        registry.put('B', new BackwardAction());
        registry.put('S', new SpeedBoostAction());
        registry.put('U', new UTurnAction());
        registry.put('Z', new UndoAction());
        registry.put('Y', new RedoAction());
    }

    /**
     * Registers a custom action for the given command character.
     *
     * @param command the character that triggers this action
     * @param action  the action to execute
     */
    public void register(char command, Action action) {
        registry.put(command, action);
    }

    /**
     * Parses a command string into a list of actions.
     *
     * @param commands the command string (e.g. "LMRM"), or null
     * @return ordered list of actions; empty list for null or empty input
     * @throws InvalidActionException if any character is not registered
     */
    public List<Action> parse(String commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }

        List<Action> actions = new ArrayList<>(commands.length());
        for (int i = 0; i < commands.length(); i++) {
            char ch = commands.charAt(i);
            Action action = registry.get(ch);
            if (action == null) {
                throw new InvalidActionException(ch, i);
            }
            actions.add(action);
        }
        return actions;
    }
}
