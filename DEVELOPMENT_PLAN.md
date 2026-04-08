# Rover Control System — Development Plan

## Overview

CLI system controlling a robotic rover on a 2D grid. Rover starts at (0,0) facing North.
Commands: L (turn left 90°), R (turn right 90°), M (move forward). Output: `"x:y"`.

## Design

- **Strategy pattern** for extensible actions via `Action` interface
- **Immutable state** via Java 17 records (`Position`, `RoverState`)
- **Registry-based parser** (`ActionParser`) using `Map<Character, Action>`
- **Domain exception** (`InvalidActionException`) for illegal input

## Roadmap

### V0 (MVP) — Completed
- Infinite plane, single rover, three actions (L, R, M)
- Full TDD with 95%+ branch coverage

### V1 — Concurrency & Action Enrichment

#### Concurrency Strategy: Hybrid `AtomicReference` + `synchronized`

**Problem:** `Rover` has two mutable fields (`position`, `direction`) updated non-atomically. Under concurrent access, readers can observe inconsistent state (e.g., new position + old direction).

**Approach:** Combine lock-free reads with mutually exclusive writes.

| Operation | Mechanism | Rationale |
|-----------|-----------|-----------|
| Read state (`getPosition`, `getDirection`, `getState`) | `AtomicReference.get()` — no lock | High-throughput reads for production scenarios (many observers, few commands) |
| Write state (`execute`) | `synchronized` on Rover instance | Action executes exactly once — safe for future actions with side effects (logging, collision checks, external calls) |

**Why not pure `AtomicReference` (CAS)?** CAS retries the lambda on contention, so actions with side effects would execute multiple times. Since future actions may involve logging, event emission, or external system calls, `synchronized` writes are safer and more scalable as actions grow in complexity.

**Why not pure `synchronized`?** Reads would contend with writes unnecessarily. In a production multi-user scenario (read-heavy), lock-free reads avoid bottlenecks.

#### Scope
- Refactor `Rover` to store state as `AtomicReference<RoverState>`
- Thread-safe `ActionParser` registry (`ConcurrentHashMap`)
- Concurrency test suite (multi-thread correctness, state consistency)
- Additional actions (TBD — user will define further)

### V2 — Multi-Rover Control
- Support multiple rovers on the same plane
- Rover registry / fleet management
- Collision detection and coordination between rovers

### V3 — Real-Time UI
- Visual interface showing rover position and movement in real time
- Live rendering of action execution (not just final coordinates)
- Interactive control panel

## Implementation Order

### V0
1. `Direction` enum (leaf — no deps)
2. `Position` record (depends on Direction)
3. `RoverState` record (pure data carrier)
4. `Action` interface + TurnLeft/TurnRight/MoveForward
5. `InvalidActionException` + `ActionParser`
6. `Rover`
7. `App` integration
8. Coverage verification

### V1
1. Refactor `Rover`: merge `position` + `direction` into `AtomicReference<RoverState>`
2. `execute()` methods add `synchronized` — action executes exactly once
3. `getPosition()` / `getDirection()` delegate to `AtomicReference.get()` — lock-free reads
4. Add `getState()` method — returns immutable `RoverState` snapshot
5. `execute(List<Action>)` holds lock for entire batch — atomic sequence execution
6. `ActionParser`: `HashMap` → `ConcurrentHashMap` for thread-safe register + parse
7. Add `RoverConcurrencyTest`:
   - Multi-thread MoveForward: verify total distance correctness
   - Concurrent read/write: verify `getState()` always returns consistent snapshot
   - Concurrent full rotations (LLLL): verify direction invariant
   - ActionParser concurrent register + parse: verify no exceptions or data loss
8. Coverage verification (maintain 95%+ branch coverage)
