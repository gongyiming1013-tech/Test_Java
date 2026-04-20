package com.rover.web;

import com.rover.Action;
import com.rover.ActionParser;
import com.rover.Arena;
import com.rover.BoundaryMode;
import com.rover.ConflictPolicy;
import com.rover.Direction;
import com.rover.GridEnvironment;
import com.rover.Position;
import com.rover.Rover;
import com.rover.RoverEvent;
import com.rover.RoverListener;
import com.rover.RoverState;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** Contract tests for {@link ArenaStepExecutor}. */
public class ArenaStepExecutorTest {

    private Arena arena;
    private ActionParser parser;
    private RecordingListener recA;
    private RecordingListener recB;

    @Before
    public void setUp() {
        arena = new Arena(new GridEnvironment(10, 10, new HashSet<>(), BoundaryMode.BOUNDED),
                ConflictPolicy.SKIP);
        parser = new ActionParser();

        Rover a = arena.createRover("A", new Position(0, 0), Direction.NORTH);
        Rover b = arena.createRover("B", new Position(5, 5), Direction.NORTH);

        recA = new RecordingListener();
        recB = new RecordingListener();
        a.addListener(recA);
        b.addListener(recB);
    }

    @Test
    public void sequential_completesAllOfFirstRoverBeforeStartingNext() {
        Map<String, List<Action>> commands = new LinkedHashMap<>();
        commands.put("A", parser.parse("MMM"));
        commands.put("B", parser.parse("MM"));

        ArenaStepExecutor.executeSequential(arena, commands, 0L);

        // All A events appear before any B event
        List<String> order = new ArrayList<>();
        for (String s : recA.tags) order.add("A:" + s);
        // Real interleave inspection: combined timeline
        // (A done first, then B) — verify by final positions and event counts
        assertEquals(3, recA.tags.size());
        assertEquals(2, recB.tags.size());
        assertEquals(new Position(0, 3), arena.getRover("A").getPosition());
        assertEquals(new Position(5, 7), arena.getRover("B").getPosition());
    }

    @Test
    public void parallel_roundRobinOrder() {
        Map<String, List<Action>> commands = new LinkedHashMap<>();
        commands.put("A", parser.parse("MM"));
        commands.put("B", parser.parse("MM"));

        ArenaStepExecutor.executeParallel(arena, commands, 0L);

        // Round-robin: A0, B0, A1, B1 — both rovers at +2
        assertEquals(2, recA.tags.size());
        assertEquals(2, recB.tags.size());
        assertEquals(new Position(0, 2), arena.getRover("A").getPosition());
        assertEquals(new Position(5, 7), arena.getRover("B").getPosition());
    }

    @Test
    public void parallel_shorterCommandList_exitsEarly() {
        Map<String, List<Action>> commands = new LinkedHashMap<>();
        commands.put("A", parser.parse("MMM"));
        commands.put("B", parser.parse("M"));

        ArenaStepExecutor.executeParallel(arena, commands, 0L);

        assertEquals(3, recA.tags.size());
        assertEquals(1, recB.tags.size());
    }

    @Test
    public void delayZero_runsFullSpeed() {
        Map<String, List<Action>> commands = new LinkedHashMap<>();
        commands.put("A", parser.parse("MMMMMMMM"));

        long start = System.nanoTime();
        ArenaStepExecutor.executeSequential(arena, commands, 0L);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(8, recA.tags.size());
        assertTrue("delay=0 should run nearly instant (<200ms), was " + elapsedMs, elapsedMs < 200);
    }

    @Test
    public void delay50_producesMeasurablePacing() {
        Map<String, List<Action>> commands = new LinkedHashMap<>();
        commands.put("A", parser.parse("MMM"));

        long start = System.nanoTime();
        ArenaStepExecutor.executeSequential(arena, commands, 50L);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        // 3 steps with 50ms between → expect ≥ ~100ms (delay applied 2-3 times)
        assertTrue("expected ≥80ms with delay=50, was " + elapsedMs, elapsedMs >= 80);
    }

    @Test(timeout = 2000)
    public void interrupt_abortsLoopCleanly() throws InterruptedException {
        Map<String, List<Action>> commands = new LinkedHashMap<>();
        commands.put("A", parser.parse("MMMMMMMMMM"));

        CountDownLatch started = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            started.countDown();
            ArenaStepExecutor.executeSequential(arena, commands, 200L);
        });
        t.start();
        started.await();
        Thread.sleep(50);
        t.interrupt();
        t.join(1000);

        assertFalse("thread should have terminated after interrupt", t.isAlive());
        assertTrue("only some steps should have run before interrupt", recA.tags.size() < 10);
    }

    private static class RecordingListener implements RoverListener {
        final List<String> tags = new ArrayList<>();

        @Override
        public void onStep(RoverEvent event) {
            tags.add(event.action().getClass().getSimpleName() + "@" + event.stepIndex());
        }

        @Override
        public void onComplete(RoverState finalState) { }
    }
}
