package com.rover.web;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

/**
 * Embeds a Javalin HTTP server that serves the Rover Web UI.
 *
 * <p>Starts an instance on a configurable port (default 8080), registers the
 * REST routes backed by a {@link SessionManager}, and serves the static
 * frontend assets (HTML/CSS/JS) from the classpath {@code /public} directory.</p>
 *
 * <p>Usage: {@code java -jar rover.jar --web [--port N]}.</p>
 */
public class WebApp {

    /** Default TCP port if {@code --port} is not provided. */
    public static final int DEFAULT_PORT = 8080;

    private final int port;
    private final SessionManager sessionManager;
    private Javalin javalin;

    /**
     * Creates a web app that will listen on the given port.
     *
     * @param port TCP port
     */
    public WebApp(int port) {
        this(port, new SessionManager());
    }

    /**
     * Creates a web app with a custom {@link SessionManager}. Used by tests.
     *
     * @param port           TCP port
     * @param sessionManager the session registry to use
     */
    public WebApp(int port, SessionManager sessionManager) {
        this.port = port;
        this.sessionManager = sessionManager;
    }

    /**
     * Returns the port this server is configured to listen on.
     *
     * @return the TCP port
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the session manager instance backing this server.
     *
     * @return the session manager
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Starts the embedded server. Blocks until the server is listening, then
     * returns. Registers a JVM shutdown hook to stop gracefully on exit.
     *
     * @throws IllegalStateException if already started
     */
    public void start() {
        if (javalin != null) {
            throw new IllegalStateException("server already started");
        }

        RoverController controller = new RoverController(sessionManager);

        javalin = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
            config.showJavalinBanner = false;
        });

        javalin.post("/api/session", controller::createSession);
        javalin.put("/api/session/{id}/config", controller::configure);
        javalin.post("/api/session/{id}/run", controller::run);
        javalin.post("/api/session/{id}/reset", controller::reset);
        javalin.get("/api/session/{id}/state", controller::getState);
        javalin.delete("/api/session/{id}", controller::deleteSession);
        javalin.before("/api/session/{id}/events", ctx -> {
            String id = ctx.pathParam("id");
            if (sessionManager.getSession(id) == null) {
                ctx.status(io.javalin.http.HttpStatus.NOT_FOUND);
                ctx.json(new WebError("SESSION_NOT_FOUND", "session not found: " + id));
                ctx.skipRemainingHandlers();
            }
        });
        javalin.sse("/api/session/{id}/events", controller::subscribeEvents);

        javalin.start(port);

        Runtime.getRuntime().addShutdownHook(new Thread(this::stopQuietly, "webapp-shutdown"));
    }

    /**
     * Stops the embedded server cleanly, releasing the port.
     */
    public void stop() {
        if (javalin != null) {
            javalin.stop();
            javalin = null;
        }
        sessionManager.shutdown();
    }

    private void stopQuietly() {
        try {
            stop();
        } catch (Exception ignored) {
            // shutdown hook — swallow
        }
    }

    /**
     * Returns true if the server is currently running.
     *
     * @return running flag
     */
    public boolean isRunning() {
        return javalin != null;
    }

    /**
     * CLI entry point. Parses {@code --port N} (defaulting to {@link #DEFAULT_PORT})
     * and starts the server.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            }
        }
        WebApp app = new WebApp(port);
        app.start();
        System.out.println("Rover Web UI running at http://localhost:" + port);
    }
}
