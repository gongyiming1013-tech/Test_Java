package com.rover.web;

import com.rover.Action;
import com.rover.ActionParser;
import com.rover.Arena;
import com.rover.MoveBlockedException;
import com.rover.Position;
import com.rover.Rover;
import com.rover.RoverEvent;
import com.rover.RoverListener;
import com.rover.RoverState;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Per-browser session state: owns an {@link Arena}, tracks the current config,
 * accumulates path trails and stats across runs, and maintains SSE subscribers
 * for real-time event streaming (V6b).
 *
 * <p>Thread-safety: all mutable state is guarded by {@code synchronized(this)}
 * except {@code lastAccessMillis} (volatile for fast TTL reads) and
 * {@code running} (volatile for fast progress checks). Runs execute on a
 * private single-threaded executor so multiple requests to the same session
 * serialize automatically.</p>
 */
public class Session {

    private final String id;
    private final Clock clock;
    private final ExecutorService executor;

    private volatile long lastAccessMillis;
    private volatile boolean running = false;

    // Guarded by synchronized(this)
    private ArenaConfig config;
    private Arena arena;
    private final Map<String, List<Position>> trails = new LinkedHashMap<>();
    private RunStats stats = RunStats.empty();
    private int runCount = 0;

    /** SSE subscribers receiving real-time {@code step} and {@code complete} events (V6b). */
    private final CopyOnWriteArrayList<SseSink> subscribers = new CopyOnWriteArrayList<>();

    /**
     * Creates a session with a UUID and the given clock (used for TTL tracking).
     *
     * @param id    session identifier (typically a UUID)
     * @param clock clock for timestamping last-access
     */
    public Session(String id, Clock clock) {
        this.id = id;
        this.clock = clock;
        this.lastAccessMillis = clock.millis();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "session-" + id);
            t.setDaemon(true);
            return t;
        });
    }

    public String getId() {
        return id;
    }

    public long getLastAccessMillis() {
        return lastAccessMillis;
    }

    public void touch() {
        this.lastAccessMillis = clock.millis();
    }

    /**
     * Validates the config and replaces the current arena with a fresh one.
     * Resets trails, stats, and run counter.
     */
    public synchronized void configure(ArenaConfig config) {
        // buildArena throws ConfigValidationException on invalid input;
        // state is only updated once validation has passed.
        Arena newArena = ArenaConfigMapper.buildArena(config);

        this.arena = newArena;
        this.config = config;
        resetState();
        touch();
    }

    /**
     * Rebuilds the Arena from the stored config, returning rovers to their
     * starting positions. Resets trails, stats, and run counter. Does NOT
     * require the frontend to re-send the config.
     *
     * @throws IllegalStateException if not configured or currently running
     */
    public synchronized void resetToStart() {
        if (config == null) {
            throw new IllegalStateException("session not configured");
        }
        if (running) {
            throw new IllegalStateException("cannot reset while running");
        }
        this.arena = ArenaConfigMapper.buildArena(config);
        resetState();
        touch();
    }

    /** Clears trails, stats, and run counter. */
    private void resetState() {
        this.trails.clear();
        for (RoverSpecDto spec : config.rovers()) {
            this.trails.put(spec.id(), new ArrayList<>());
        }
        this.stats = RunStats.empty();
        this.runCount = 0;
    }

    public synchronized ArenaConfig getConfig() {
        return config;
    }

    public synchronized Arena getArena() {
        return arena;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Executes commands from the stored config on the existing Arena.
     */
    public synchronized Future<?> run() {
        return run(null);
    }

    /**
     * V6b overload: executes commands with an optional per-run {@code delayMs}
     * override. When {@code overrideDelayMs} is non-null, it takes precedence
     * over {@link ArenaConfig#delayMs} for this single run only (subsequent
     * runs without override fall back to the config value). Used by the
     * "Skip animation" UI button (override = 0) and the speed slider.
     *
     * @param overrideCommands rover ID → command string, or null to use config's commands
     * @param overrideDelayMs  per-run pacing override in ms, or null to use config default
     * @throws IllegalStateException if not configured or already running
     */
    public synchronized Future<?> run(Map<String, String> overrideCommands,
                                      Long overrideDelayMs) {
        if (arena == null || config == null) {
            throw new IllegalStateException("session not configured");
        }
        if (running) {
            throw new IllegalStateException("session already running");
        }
        running = true;

        final Arena capturedArena = arena;
        final ArenaConfig capturedConfig = config;
        final Map<String, String> capturedCommands = overrideCommands;
        final long effectiveDelayMs = overrideDelayMs != null
                ? overrideDelayMs
                : ArenaConfigMapper.validateAndDefaultDelay(capturedConfig.delayMs());

        return executor.submit(() -> {
            try {
                runInternal(capturedArena, capturedConfig, capturedCommands, effectiveDelayMs);
            } finally {
                running = false;
            }
        });
    }

    /**
     * Registers an SSE subscriber that will receive {@code state}, {@code step},
     * and {@code complete} events for this session. Idempotent — adding the
     * same sink twice is allowed but only registers once.
     *
     * @param sink the subscriber
     */
    public void subscribe(SseSink sink) {
        subscribers.addIfAbsent(sink);
    }

    /**
     * Removes a previously registered subscriber. No-op if not registered.
     *
     * @param sink the subscriber
     * @return {@code true} if the sink was removed
     */
    public boolean unsubscribe(SseSink sink) {
        return subscribers.remove(sink);
    }

    /**
     * Sends a named event with the given payload to every live subscriber.
     * Subscribers whose {@link SseSink#terminated()} returns {@code true} or
     * which throw on {@code sendEvent} are removed in-place.
     *
     * @param eventName SSE event name (e.g., {@code "step"}, {@code "complete"})
     * @param payload   JSON-serializable data
     */
    public void broadcast(String eventName, Object payload) {
        List<SseSink> dead = null;
        for (SseSink sink : subscribers) {
            if (sink.terminated()) {
                if (dead == null) dead = new ArrayList<>();
                dead.add(sink);
                continue;
            }
            try {
                sink.sendEvent(eventName, payload);
            } catch (Throwable t) {
                if (dead == null) dead = new ArrayList<>();
                dead.add(sink);
            }
        }
        if (dead != null) subscribers.removeAll(dead);
    }

    /**
     * Returns a snapshot of the current subscriber count. Primarily for tests
     * and metrics.
     *
     * @return live subscriber count
     */
    public int getSubscriberCount() {
        return subscribers.size();
    }

    /**
     * Executes commands on the existing Arena. If {@code overrideCommands} is
     * provided (non-null), those commands are used instead of the ones stored
     * in the config. This enables "Continue Run" — the frontend sends new
     * commands without reconfiguring (which would rebuild the Arena).
     *
     * @param overrideCommands rover ID → command string, or null to use config's commands
     * @throws IllegalStateException if not configured or already running
     */
    public synchronized Future<?> run(Map<String, String> overrideCommands) {
        return run(overrideCommands, null);
    }

    /** Runs the commands synchronously. Called on the session executor thread. */
    private void runInternal(Arena arena, ArenaConfig config, Map<String, String> overrideCommands, long delayMs) {
        long startNanos = System.nanoTime();
        int[] counters = {0, 0}; // [totalSteps, blockedCount]

        Map<String, List<RoverListener>> attachedListeners = new LinkedHashMap<>();
        Map<String, List<Action>> commandsMap = new LinkedHashMap<>();
        ActionParser parser = new ActionParser();

        synchronized (this) {
            boolean firstRun = (runCount == 0);

            for (RoverSpecDto spec : config.rovers()) {
                String roverId = spec.id();
                Rover rover = arena.getRover(roverId);

                // Only seed trail with starting/current position on first run;
                // subsequent runs continue from wherever the trail already ends.
                if (firstRun) {
                    trails.get(roverId).add(rover.getPosition());
                }

                List<RoverListener> listeners = new ArrayList<>(2);
                RoverListener trailListener = new TrailListener(roverId, counters);
                rover.addListener(trailListener);
                listeners.add(trailListener);

                RoverListener sseListener = new SseRoverListener(roverId, this);
                rover.addListener(sseListener);
                listeners.add(sseListener);

                attachedListeners.put(roverId, listeners);

                String cmds;
                if (overrideCommands != null && overrideCommands.containsKey(roverId)) {
                    cmds = overrideCommands.get(roverId);
                } else {
                    cmds = spec.commands();
                }
                commandsMap.put(roverId, parser.parse(cmds == null ? "" : cmds));
            }

            runCount++;
        }

        try {
            if (config.parallel()) {
                ArenaStepExecutor.executeParallel(arena, commandsMap, delayMs);
            } else {
                ArenaStepExecutor.executeSequential(arena, commandsMap, delayMs);
            }
        } catch (MoveBlockedException ignored) {
            // FAIL policy aborts — stats/trails already reflect the partial run
        } finally {
            // Detach listeners
            for (Map.Entry<String, List<RoverListener>> entry : attachedListeners.entrySet()) {
                Rover r = arena.getRover(entry.getKey());
                for (RoverListener l : entry.getValue()) r.removeListener(l);
            }
        }

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        RunStats updatedStats;
        int currentRunCount;
        Map<String, RoverStateDto> finalStates = new LinkedHashMap<>();
        synchronized (this) {
            // Accumulate stats across runs (not replace)
            RunStats prev = stats;
            stats = new RunStats(
                    prev.totalSteps() + counters[0],
                    prev.blockedCount() + counters[1],
                    prev.durationMs() + durationMs,
                    config.rovers().size(),
                    config.obstacles() == null ? 0 : config.obstacles().size()
            );
            updatedStats = stats;
            currentRunCount = runCount;
            for (RoverSpecDto spec : config.rovers()) {
                Rover r = arena.getRover(spec.id());
                RoverState s = r.getState();
                finalStates.put(spec.id(), new RoverStateDto(
                        s.position().x(), s.position().y(), s.direction().name()));
            }
        }

        broadcast("complete", new RunCompleteDto(
                UUID.randomUUID().toString(),
                currentRunCount,
                updatedStats,
                finalStates));
    }

    /**
     * Returns the trails accumulated during the most recent run, keyed by rover ID.
     * Returns a defensive copy so callers cannot mutate session state.
     */
    public synchronized Map<String, List<Position>> getTrails() {
        Map<String, List<Position>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Position>> entry : trails.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    public synchronized RunStats getStats() {
        return stats;
    }

    /**
     * Returns how many times {@link #run()} has been invoked since the last
     * configure or reset.
     *
     * @return run count
     */
    public synchronized int getRunCount() {
        return runCount;
    }

    /**
     * Builds a full {@link SessionSnapshot} for the {@code GET /state} endpoint.
     */
    public synchronized SessionSnapshot getSnapshot() {
        Map<String, RoverStateDto> roverStates = new LinkedHashMap<>();
        Map<String, List<PositionDto>> trailDtos = new LinkedHashMap<>();

        if (config != null) {
            for (RoverSpecDto spec : config.rovers()) {
                String roverId = spec.id();
                Rover rover = arena.getRover(roverId);
                RoverState state = rover.getState();
                roverStates.put(roverId, new RoverStateDto(
                        state.position().x(),
                        state.position().y(),
                        state.direction().name()));

                List<PositionDto> trailDto = new ArrayList<>();
                for (Position p : trails.get(roverId)) {
                    trailDto.add(new PositionDto(p.x(), p.y()));
                }
                trailDtos.put(roverId, trailDto);
            }
        }

        ViewportDto viewport = computeViewport();

        return new SessionSnapshot(
                id,
                config,
                roverStates,
                trailDtos,
                viewport,
                stats,
                running);
    }

    /** Computes the viewport: bounded grid → exact; unbounded → auto-fit. */
    private ViewportDto computeViewport() {
        if (config == null) {
            return ViewportCalculator.autoFit(Collections.emptyList());
        }
        if (!config.isUnbounded()) {
            return ViewportCalculator.forBoundedGrid(config.width(), config.height());
        }
        // Unbounded: collect all points (rovers, trails, obstacles) for auto-fit
        List<Position> points = new ArrayList<>();
        for (RoverSpecDto spec : config.rovers()) {
            Rover rover = arena.getRover(spec.id());
            points.add(rover.getPosition());
        }
        for (List<Position> trail : trails.values()) {
            points.addAll(trail);
        }
        if (config.obstacles() != null) {
            for (PositionDto p : config.obstacles()) {
                points.add(new Position(p.x(), p.y()));
            }
        }
        return ViewportCalculator.autoFit(points);
    }

    /** Listener that appends unique positions to a rover's trail and increments counters. */
    private final class TrailListener implements RoverListener {
        private final String roverId;
        private final int[] counters;

        TrailListener(String roverId, int[] counters) {
            this.roverId = roverId;
            this.counters = counters;
        }

        @Override
        public void onStep(RoverEvent event) {
            synchronized (Session.this) {
                List<Position> trail = trails.get(roverId);
                Position newPos = event.newState().position();
                if (trail.isEmpty() || !trail.get(trail.size() - 1).equals(newPos)) {
                    trail.add(newPos);
                }
                counters[0]++;
                if (event.blocked()) counters[1]++;
            }
        }

        @Override
        public void onComplete(RoverState finalState) {
            // no-op
        }
    }
}
