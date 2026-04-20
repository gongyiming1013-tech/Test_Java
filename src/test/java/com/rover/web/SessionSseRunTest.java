package com.rover.web;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

/**
 * Integration of {@link Session} runs with SSE subscribers — verifies that a
 * subscriber receives an ordered stream of {@code step} events followed by
 * a single {@code complete} event, and that the per-run {@code delayMs}
 * override flows through the correct pacing path.
 */
public class SessionSseRunTest {

    private Session session;
    private RecordingSseSink sink;

    @Before
    public void setUp() {
        session = new Session("test", Clock.systemUTC());
        sink = new RecordingSseSink();
    }

    @Test
    public void subscribeBeforeRun_receivesAllStepEventsInOrder() throws Exception {
        session.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMR")), false, 0L));
        session.subscribe(sink);

        Future<?> f = session.run();
        await(f);

        long stepCount = sink.events.stream().filter(e -> "step".equals(e.name)).count();
        assertEquals("3 step events expected", 3, stepCount);

        // step events in execution order
        int firstStepIdx = -1;
        int lastStepIdx = -1;
        for (RecordingSseSink.Event ev : sink.events) {
            if (!"step".equals(ev.name)) continue;
            int idx = ((RoverEventDto) ev.payload).stepIndex();
            if (firstStepIdx == -1) firstStepIdx = idx;
            lastStepIdx = idx;
        }
        assertEquals(0, firstStepIdx);
        assertEquals(2, lastStepIdx);
    }

    @Test
    public void run_emitsExactlyOneCompleteEventAfterAllSteps() throws Exception {
        session.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MM")), false, 0L));
        session.subscribe(sink);
        await(session.run());

        long completeCount = sink.events.stream().filter(e -> "complete".equals(e.name)).count();
        assertEquals(1, completeCount);

        RecordingSseSink.Event last = sink.events.get(sink.events.size() - 1);
        assertEquals("complete must be the final event", "complete", last.name);
        assertTrue("payload should be RunCompleteDto", last.payload instanceof RunCompleteDto);
    }

    @Test
    public void completeEvent_carriesFinalStatesAndStats() throws Exception {
        session.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMM")), false, 0L));
        session.subscribe(sink);
        await(session.run());

        RunCompleteDto dto = (RunCompleteDto) sink.events.get(sink.events.size() - 1).payload;
        assertNotNull(dto.runId());
        assertEquals(1, dto.runCount());
        assertEquals(3, dto.stats().totalSteps());
        assertNotNull(dto.finalStates().get("R1"));
        assertEquals(0, dto.finalStates().get("R1").x());
        assertEquals(3, dto.finalStates().get("R1").y());
    }

    @Test
    public void multiRover_eventsCarryCorrectRoverIds() throws Exception {
        session.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(
                        new RoverSpecDto("A", 0, 0, "N", "M"),
                        new RoverSpecDto("B", 5, 5, "N", "M")),
                false, 0L));
        session.subscribe(sink);
        await(session.run());

        boolean sawA = false, sawB = false;
        for (RecordingSseSink.Event ev : sink.events) {
            if (!"step".equals(ev.name)) continue;
            String id = ((RoverEventDto) ev.payload).roverId();
            if ("A".equals(id)) sawA = true;
            if ("B".equals(id)) sawB = true;
        }
        assertTrue("expected step from A", sawA);
        assertTrue("expected step from B", sawB);
    }

    @Test
    public void runWithOverrideDelay_zeroRunsFastEvenIfConfigSlow() throws Exception {
        // Config says 1000ms but override is 0 → run should finish quickly
        session.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MMMM")), false, 1000L));
        session.subscribe(sink);

        long start = System.nanoTime();
        Future<?> f = session.run(null, 0L);
        await(f);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertTrue("override delay=0 should finish < 500ms (got " + elapsedMs + ")",
                elapsedMs < 500);
    }

    @Test
    public void runTwice_runCountIncrements_statsAccumulate() throws Exception {
        session.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MM")), false, 0L));
        session.subscribe(sink);

        await(session.run());
        await(session.run());

        long completeCount = sink.events.stream().filter(e -> "complete".equals(e.name)).count();
        assertEquals(2, completeCount);

        // Last complete event's runCount should be 2
        RecordingSseSink.Event last = sink.events.get(sink.events.size() - 1);
        RunCompleteDto dto = (RunCompleteDto) last.payload;
        assertEquals(2, dto.runCount());
        assertEquals("stats accumulate across runs", 4, dto.stats().totalSteps());
    }

    @Test
    public void unsubscribedSinkDoesNotReceiveEvents() throws Exception {
        session.configure(new ArenaConfig(10, 10, false, List.of(), "fail",
                List.of(new RoverSpecDto("R1", 0, 0, "N", "MM")), false, 0L));
        session.subscribe(sink);
        session.unsubscribe(sink);
        await(session.run());

        assertTrue("unsubscribed sink should receive no events", sink.events.isEmpty());
    }

    private static void await(Future<?> f) throws InterruptedException, ExecutionException {
        try {
            f.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("run did not complete in time");
        }
    }
}
