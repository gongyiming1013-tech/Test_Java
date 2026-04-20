package com.rover.web;

import com.rover.Direction;
import com.rover.MoveForwardAction;
import com.rover.Position;
import com.rover.RoverEvent;
import com.rover.RoverState;
import com.rover.TurnLeftAction;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Contract tests for {@link SseRoverListener} — verifies it builds the correct
 * {@link RoverEventDto} from a domain {@link RoverEvent} and broadcasts it on
 * the bound session under the {@code "step"} event name.
 */
public class SseRoverListenerTest {

    private Session session;
    private RecordingSseSink sink;

    @Before
    public void setUp() {
        session = new Session("test", Clock.systemUTC());
        sink = new RecordingSseSink();
        session.subscribe(sink);
    }

    @Test
    public void onStep_broadcastsAsStepEvent() {
        SseRoverListener listener = new SseRoverListener("R1", session);

        RoverEvent event = new RoverEvent(
                new RoverState(new Position(0, 0), Direction.NORTH),
                new RoverState(new Position(0, 1), Direction.NORTH),
                new MoveForwardAction(),
                0, 3, false);

        listener.onStep(event);

        assertEquals(1, sink.events.size());
        RecordingSseSink.Event e = sink.events.get(0);
        assertEquals("step", e.name);
        assertTrue("payload must be RoverEventDto", e.payload instanceof RoverEventDto);
    }

    @Test
    public void onStep_dtoFieldsMirrorDomainEvent() {
        SseRoverListener listener = new SseRoverListener("R-alpha", session);

        RoverEvent event = new RoverEvent(
                new RoverState(new Position(2, 3), Direction.EAST),
                new RoverState(new Position(2, 3), Direction.NORTH),
                new TurnLeftAction(),
                4, 10, false);

        listener.onStep(event);

        RoverEventDto dto = (RoverEventDto) sink.events.get(0).payload;
        assertEquals("R-alpha", dto.roverId());
        assertEquals(4, dto.stepIndex());
        assertEquals(10, dto.totalSteps());
        assertEquals(2, dto.prevX());
        assertEquals(3, dto.prevY());
        assertEquals("EAST", dto.prevDir());
        assertEquals(2, dto.newX());
        assertEquals(3, dto.newY());
        assertEquals("NORTH", dto.newDir());
        assertEquals("TurnLeft", dto.action());
        assertFalse(dto.blocked());
    }

    @Test
    public void onStep_blockedFlagPreserved() {
        SseRoverListener listener = new SseRoverListener("R1", session);

        RoverEvent blocked = new RoverEvent(
                new RoverState(new Position(0, 0), Direction.NORTH),
                new RoverState(new Position(0, 0), Direction.NORTH),
                new MoveForwardAction(),
                0, 1, true);

        listener.onStep(blocked);

        RoverEventDto dto = (RoverEventDto) sink.events.get(0).payload;
        assertTrue(dto.blocked());
    }

    @Test
    public void onComplete_emitsNothing_sessionEmitsRunLevelComplete() {
        SseRoverListener listener = new SseRoverListener("R1", session);
        listener.onComplete(new RoverState(new Position(0, 0), Direction.NORTH));
        assertTrue("listener.onComplete must be a no-op", sink.events.isEmpty());
    }

    @Test
    public void actionLabel_stripsActionSuffix() {
        assertEquals("MoveForward", SseRoverListener.actionLabel("MoveForwardAction"));
        assertEquals("TurnLeft", SseRoverListener.actionLabel("TurnLeftAction"));
        assertEquals("TurnRight", SseRoverListener.actionLabel("TurnRightAction"));
        assertEquals("Backward", SseRoverListener.actionLabel("BackwardAction"));
    }

    @Test
    public void actionLabel_classNameWithoutSuffix_returnedAsIs() {
        assertEquals("Custom", SseRoverListener.actionLabel("Custom"));
    }

    @Test
    public void multipleListeners_eachEmitsForOwnRover() {
        SseRoverListener a = new SseRoverListener("A", session);
        SseRoverListener b = new SseRoverListener("B", session);

        RoverEvent event = new RoverEvent(
                new RoverState(new Position(0, 0), Direction.NORTH),
                new RoverState(new Position(0, 1), Direction.NORTH),
                new MoveForwardAction(), 0, 1, false);

        a.onStep(event);
        b.onStep(event);

        assertEquals(2, sink.events.size());
        assertEquals("A", ((RoverEventDto) sink.events.get(0).payload).roverId());
        assertEquals("B", ((RoverEventDto) sink.events.get(1).payload).roverId());
    }
}
