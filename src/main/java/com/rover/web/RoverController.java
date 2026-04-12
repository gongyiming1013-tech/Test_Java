package com.rover.web;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Map;

/**
 * Javalin route handlers for the V6 REST API.
 *
 * <p>All handlers are thin wrappers that delegate to {@link SessionManager}
 * and {@link Session}. Validation errors from {@link ArenaConfigMapper} are
 * translated into {@link WebError} JSON responses with appropriate HTTP status
 * codes (400 for validation, 404 for missing session, 409 for conflict).</p>
 */
public class RoverController {

    private final SessionManager sessionManager;

    /**
     * Creates a controller backed by the given session manager.
     *
     * @param sessionManager the shared session registry
     */
    public RoverController(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Handles {@code POST /api/session}. Creates a new session and returns
     * {@code {"sessionId": "..."}}.
     */
    public void createSession(Context ctx) {
        Session session = sessionManager.createSession();
        ctx.status(HttpStatus.OK);
        ctx.json(Map.of("sessionId", session.getId()));
    }

    /**
     * Handles {@code PUT /api/session/{id}/config}. Validates and stores the
     * submitted {@link ArenaConfig}. Returns {@code 200} on success, {@code 400}
     * on validation error, {@code 404} on unknown session.
     */
    public void configure(Context ctx) {
        Session session = requireSession(ctx);
        if (session == null) return;

        try {
            ArenaConfig config = ctx.bodyAsClass(ArenaConfig.class);
            session.configure(config);
            session.touch();
            ctx.status(HttpStatus.OK);
            ctx.json(Map.of("status", "configured"));
        } catch (ConfigValidationException e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new WebError(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new WebError("INVALID_BODY", "request body is not valid JSON: " + e.getMessage()));
        }
    }

    /**
     * Handles {@code POST /api/session/{id}/run}. Triggers asynchronous
     * execution. Accepts an optional JSON body {@code {"commands": {"R1": "MMR"}}}
     * to override the stored commands (enables Continue Run without reconfiguring).
     * Returns {@code 202} on success, {@code 409} if already running or not configured.
     */
    public void run(Context ctx) {
        Session session = requireSession(ctx);
        if (session == null) return;

        try {
            Map<String, String> overrideCommands = null;
            String body = ctx.body();
            if (body != null && !body.isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    var parsed = ctx.bodyAsClass(Map.class);
                    Object cmds = parsed.get("commands");
                    if (cmds instanceof Map<?,?> cmdMap) {
                        overrideCommands = new java.util.LinkedHashMap<>();
                        for (var entry : cmdMap.entrySet()) {
                            overrideCommands.put(String.valueOf(entry.getKey()),
                                    String.valueOf(entry.getValue()));
                        }
                    }
                } catch (Exception ignored) {
                    // Body is not valid JSON or doesn't have "commands" — run with stored commands
                }
            }

            session.run(overrideCommands);
            session.touch();
            ctx.status(HttpStatus.ACCEPTED);
            ctx.json(Map.of("status", "running"));
        } catch (IllegalStateException e) {
            ctx.status(HttpStatus.CONFLICT);
            ctx.json(new WebError("CONFLICT", e.getMessage()));
        }
    }

    /**
     * Handles {@code GET /api/session/{id}/state}. Returns the full
     * {@link SessionSnapshot} for rendering.
     */
    public void getState(Context ctx) {
        Session session = requireSession(ctx);
        if (session == null) return;

        session.touch();
        ctx.status(HttpStatus.OK);
        ctx.json(session.getSnapshot());
    }

    /**
     * Handles {@code POST /api/session/{id}/reset}. Rebuilds the Arena from
     * stored config, returning rovers to starting positions. Clears trails and stats.
     * Returns the fresh snapshot. {@code 404} if session missing, {@code 409} if running
     * or not configured.
     */
    public void reset(Context ctx) {
        Session session = requireSession(ctx);
        if (session == null) return;

        try {
            session.resetToStart();
            session.touch();
            ctx.status(HttpStatus.OK);
            ctx.json(session.getSnapshot());
        } catch (IllegalStateException e) {
            ctx.status(HttpStatus.CONFLICT);
            ctx.json(new WebError("CONFLICT", e.getMessage()));
        }
    }

    /**
     * Handles {@code DELETE /api/session/{id}}. Removes the session.
     * Returns {@code 204} on success, {@code 404} if not found.
     */
    public void deleteSession(Context ctx) {
        String id = ctx.pathParam("id");
        if (sessionManager.removeSession(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(new WebError("SESSION_NOT_FOUND", "session not found: " + id));
        }
    }

    /**
     * Resolves the session ID from the path and returns the Session, or
     * writes a 404 response and returns null if not found.
     */
    private Session requireSession(Context ctx) {
        String id = ctx.pathParam("id");
        Session session = sessionManager.getSession(id);
        if (session == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(new WebError("SESSION_NOT_FOUND", "session not found: " + id));
            return null;
        }
        return session;
    }
}
