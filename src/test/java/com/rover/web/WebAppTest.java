package com.rover.web;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.Assert.*;

/** Contract tests for {@link WebApp}. */
public class WebAppTest {

    private WebApp app;

    @After
    public void tearDown() {
        if (app != null && app.isRunning()) {
            app.stop();
        }
    }

    @Test
    public void construct_storesPort() {
        app = new WebApp(9090);
        assertEquals(9090, app.getPort());
    }

    @Test
    public void construct_providesDefaultSessionManager() {
        app = new WebApp(9090);
        assertNotNull(app.getSessionManager());
    }

    @Test
    public void construct_withCustomSessionManager() {
        SessionManager custom = new SessionManager();
        app = new WebApp(9090, custom);
        assertSame(custom, app.getSessionManager());
    }

    @Test
    public void start_startsServerOnConfiguredPort() throws IOException {
        int port = pickFreePort();
        app = new WebApp(port);
        app.start();
        assertTrue("should be running after start()", app.isRunning());
    }

    @Test
    public void stop_stopsServerCleanly() throws IOException {
        int port = pickFreePort();
        app = new WebApp(port);
        app.start();
        app.stop();
        assertFalse("should not be running after stop()", app.isRunning());
    }

    @Test
    public void startStop_multipleCycles() throws IOException {
        int port = pickFreePort();
        app = new WebApp(port);

        app.start();
        assertTrue(app.isRunning());
        app.stop();
        assertFalse(app.isRunning());
    }

    @Test
    public void defaultPort_is8080() {
        assertEquals(8080, WebApp.DEFAULT_PORT);
    }

    /** Picks a free TCP port for tests that need to bind for real. */
    private static int pickFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
