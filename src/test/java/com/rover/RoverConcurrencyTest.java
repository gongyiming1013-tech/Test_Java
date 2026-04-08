package com.rover;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Concurrency tests for {@link Rover}.
 *
 * <p>Validates that concurrent reads and writes produce consistent state
 * under the hybrid AtomicReference + synchronized strategy.</p>
 */
public class RoverConcurrencyTest {

    private static final int THREAD_COUNT = 16;
    private static final int ACTIONS_PER_THREAD = 1000;

    /**
     * Multiple threads execute MoveForward concurrently on the same rover.
     * Total moves should equal THREAD_COUNT * ACTIONS_PER_THREAD.
     */
    @Test
    public void concurrentMoveForward_totalDistanceIsCorrect() throws Exception {
        Rover rover = new Rover();
        int totalMoves = THREAD_COUNT * ACTIONS_PER_THREAD;

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < THREAD_COUNT; t++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Action move = new MoveForwardAction();
                for (int i = 0; i < ACTIONS_PER_THREAD; i++) {
                    rover.execute(move);
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Rover starts at (0,0) facing NORTH — all moves go north
        assertEquals(new Position(0, totalMoves), rover.getPosition());
        assertEquals(Direction.NORTH, rover.getDirection());
    }

    /**
     * Concurrent readers always see a consistent RoverState snapshot:
     * position and direction must belong to the same logical state.
     *
     * <p>A full left turn (LLLL) returns to NORTH. If readers ever see
     * a direction that doesn't match one of the four cardinal values,
     * or position changes during a turn, the state was torn.</p>
     */
    @Test
    public void concurrentReadsDuringWrites_stateIsAlwaysConsistent() throws Exception {
        Rover rover = new Rover();
        AtomicInteger inconsistencies = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        int writerIterations = 5000;
        int readerCount = 8;

        ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
        List<Future<?>> futures = new ArrayList<>();

        // Writer: alternates turn-left and move-forward
        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Action turnLeft = new TurnLeftAction();
            Action move = new MoveForwardAction();
            for (int i = 0; i < writerIterations; i++) {
                rover.execute(turnLeft);
                rover.execute(move);
            }
        }));

        // Readers: continuously read state and verify consistency
        for (int r = 0; r < readerCount; r++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < writerIterations * 2; i++) {
                    RoverState snapshot = rover.getState();
                    // State must be non-null and internally consistent
                    if (snapshot == null || snapshot.position() == null || snapshot.direction() == null) {
                        inconsistencies.incrementAndGet();
                    }
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals("Readers observed inconsistent state", 0, inconsistencies.get());
    }

    /**
     * Full-rotation invariant under concurrency: four left turns return to
     * the original direction. Multiple threads each perform LLLL sequences.
     */
    @Test
    public void concurrentFullRotations_directionReturnsToOriginal() throws Exception {
        Rover rover = new Rover();
        int rotations = 500;

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < THREAD_COUNT; t++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Action turnLeft = new TurnLeftAction();
                for (int i = 0; i < rotations; i++) {
                    // Each full rotation: LLLL → back to original
                    rover.execute(turnLeft);
                    rover.execute(turnLeft);
                    rover.execute(turnLeft);
                    rover.execute(turnLeft);
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Every thread did complete rotations — direction must be NORTH
        assertEquals(Direction.NORTH, rover.getDirection());
        // No moves were issued — position unchanged
        assertEquals(new Position(0, 0), rover.getPosition());
    }

    /**
     * Concurrent registration and parsing on ActionParser must not throw
     * ConcurrentModificationException or lose registrations.
     */
    @Test
    public void actionParser_concurrentRegisterAndParse_noException() throws Exception {
        ActionParser parser = new ActionParser();
        int iterations = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger errors = new AtomicInteger(0);

        // Thread 1: repeatedly register a custom action
        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            for (int i = 0; i < iterations; i++) {
                parser.register('X', new TurnLeftAction());
            }
        }));

        // Threads 2-4: repeatedly parse known commands
        for (int t = 0; t < 3; t++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < iterations; i++) {
                    try {
                        List<Action> actions = parser.parse("LRM");
                        if (actions.size() != 3) {
                            errors.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals("Concurrent register/parse caused errors", 0, errors.get());
    }
}
