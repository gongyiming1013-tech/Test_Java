# Rover Control System — Development Plan

## Overview

CLI system controlling a robotic rover on a 2D grid. Rover starts at (0,0) facing North.
Commands: L (turn left 90°), R (turn right 90°), M (move forward). Output: `"x:y"`.

## Design

### V0 + V1 (Current)

#### Architecture

```
App (CLI entry point)
 └─ ActionParser  ──parse──▶  List<Action>
       │                          │
       │ registry                 │ execute
       ▼                          ▼
  ConcurrentHashMap         Rover (synchronized writes / lock-free reads)
  { 'L' → TurnLeftAction,      │
    'R' → TurnRightAction,     │ AtomicReference<RoverState>
    'M' → MoveForwardAction }  │
                                ▼
                          RoverState (immutable snapshot)
                           ├─ Position (x, y)
                           └─ Direction (NORTH/EAST/SOUTH/WEST)
```

#### Design Patterns

| Pattern | Where | Purpose |
|---------|-------|---------|
| **Strategy** | `Action` interface + concrete actions | Extensible command execution — new actions added without modifying `Rover` or `ActionParser` |
| **Immutable Value Object** | `Position` (record), `RoverState` (record) | Thread-safe state snapshots, no defensive copying needed |
| **Registry** | `ActionParser.registry` (`ConcurrentHashMap`) | Decouples command parsing from action creation; supports runtime registration |

#### V1 Concurrency Strategy: Hybrid `AtomicReference` + `synchronized`

**Problem:** `Rover` has two mutable fields (`position`, `direction`) updated non-atomically. Under concurrent access, readers can observe inconsistent state (e.g., new position + old direction).

**Approach:** Combine lock-free reads with mutually exclusive writes.

| Operation | Mechanism | Rationale |
|-----------|-----------|-----------|
| Read state (`getPosition`, `getDirection`, `getState`) | `AtomicReference.get()` — no lock | High-throughput reads for production scenarios (many observers, few commands) |
| Write state (`execute`) | `synchronized` on Rover instance | Action executes exactly once — safe for future actions with side effects (logging, collision checks, external calls) |

**Alternatives considered:**

| Approach | Pros | Cons | Verdict |
|----------|------|------|---------|
| Pure `AtomicReference` (CAS) | Lock-free reads and writes | CAS retries re-execute the action — unsafe for side effects (logging, external calls) | Rejected — future actions will have side effects |
| Pure `synchronized` | Simple, action runs exactly once | Reads contend with writes; bottleneck under read-heavy production load | Rejected — read throughput matters |
| **Hybrid (chosen)** | Lock-free reads + safe single-execution writes | Slightly more complex than pure approaches | **Adopted** — best fit for read-heavy production with extensible actions |

#### Class & Data Structure Reference

##### `Direction` — Enum
Cardinal directions with embedded movement vectors and rotation logic.

| Constant | `dx` | `dy` | `turnLeft()` | `turnRight()` |
|----------|------|------|---------------|----------------|
| `NORTH`  | 0    | 1    | `WEST`        | `EAST`         |
| `EAST`   | 1    | 0    | `NORTH`       | `SOUTH`        |
| `SOUTH`  | 0    | -1   | `EAST`        | `WEST`         |
| `WEST`   | -1   | 0    | `SOUTH`       | `NORTH`        |

##### `Position` — Record `(int x, int y)`
Immutable 2D coordinate on an infinite plane.

| Method | Signature | Description |
|--------|-----------|-------------|
| `move` | `Position move(Direction d)` | Returns new position offset by `(d.dx(), d.dy())` |

##### `RoverState` — Record `(Position position, Direction direction)`
Immutable snapshot coupling position and direction. Used as the atomic unit of state inside `Rover` and as the return type of `Action.execute()`.

##### `Action` — Interface (Strategy)
Contract for all rover commands.

| Method | Signature |
|--------|-----------|
| `execute` | `RoverState execute(Position position, Direction direction)` |

**Implementations:**

| Class | Command | Behavior |
|-------|---------|----------|
| `TurnLeftAction`    | `L` | Rotates direction 90° counter-clockwise, position unchanged |
| `TurnRightAction`   | `R` | Rotates direction 90° clockwise, position unchanged |
| `MoveForwardAction` | `M` | Advances one step in current direction, direction unchanged |

##### `ActionParser` — Class
Converts command strings into ordered `List<Action>`.

| Member | Type | Description |
|--------|------|-------------|
| `registry` | `ConcurrentHashMap<Character, Action>` | Thread-safe command → action mapping |

| Method | Signature | Description |
|--------|-----------|-------------|
| `register` | `void register(char, Action)` | Adds/overrides a command binding at runtime |
| `parse` | `List<Action> parse(String)` | Translates command string; throws `InvalidActionException` on unknown char |

##### `Rover` — Class (Thread-Safe)
Core domain object. Maintains mutable state via `AtomicReference<RoverState>`.

| Member | Type | Description |
|--------|------|-------------|
| `state` | `AtomicReference<RoverState>` | Single source of truth, enables lock-free reads |

| Method | Signature | Thread Safety | Description |
|--------|-----------|---------------|-------------|
| `execute` | `synchronized void execute(Action)` | Exclusive write | Executes one action, updates state atomically |
| `execute` | `synchronized void execute(List<Action>)` | Exclusive write | Batch execution, entire sequence under one lock |
| `getState` | `RoverState getState()` | Lock-free read | Returns immutable snapshot |
| `getPosition` | `Position getPosition()` | Lock-free read | Delegates to `state.get().position()` |
| `getDirection` | `Direction getDirection()` | Lock-free read | Delegates to `state.get().direction()` |

##### `InvalidActionException` — Class extends `IllegalArgumentException`
Thrown when `ActionParser.parse()` encounters an unregistered command character. Message includes the character and its zero-based index.

##### `App` — Class (CLI Entry Point)

| Method | Signature | Description |
|--------|-----------|-------------|
| `run` | `static String run(String commands)` | Parses + executes commands, returns `"x:y"` |
| `main` | `static void main(String[] args)` | CLI wrapper around `run()` |

---

### V2 — Geographic Constraints (Planned)

_To be completed before V2 implementation begins._

#### Strategy Comparison

| Approach | Description | Trade-offs |
|----------|-------------|------------|
| _TBD_ | _e.g., Grid as a parameter to Rover_ | _..._ |
| _TBD_ | _e.g., Grid as a separate validator_ | _..._ |
| _TBD_ | _e.g., Obstacle strategy pattern_ | _..._ |

#### Design Discussion

- **Grid representation:** How to model the finite grid? (e.g., `Grid` class with width/height, or `Boundary` interface)
- **Obstacle storage:** Data structure for obstacle positions (e.g., `Set<Position>`, spatial index)
- **Constraint enforcement:** Where in the execution flow to check constraints? (inside `Action.execute()`, in `Rover.execute()`, or via a separate `MoveValidator`)
- **Violation behavior:** What happens when a move is blocked? (ignore silently, throw exception, return blocked status)

#### Class & Data Structure Changes

_New and modified classes to be documented here._

---

### V3–V5 (Planned)

_Design sections to be added when each version enters planning._

## Roadmap

### V0 (MVP) — Completed
- Infinite plane, single rover, three actions (L, R, M)
- Full TDD with 95%+ branch coverage

### V1 — Concurrency (Completed)
- Hybrid `AtomicReference` + `synchronized` concurrency strategy (see [Design > V0 + V1](#v0--v1-current) for details)
- Refactor `Rover` to store state as `AtomicReference<RoverState>`
- Thread-safe `ActionParser` registry (`ConcurrentHashMap`)
- Concurrency test suite (multi-thread correctness, state consistency)

### V2 — Geographic Constraints
- **Grid boundaries:** Introduce a finite grid (e.g., `width × height`); rover cannot move beyond edges
- **Obstacles:** Certain cells are impassable; rover must detect and handle blocked moves
- **Strategy for constraints:** Define how rover reacts to boundary/obstacle violations (e.g., ignore move, throw exception, or find alternative path)

### V3 — Additional Commands
- Expand the `Action` set beyond L/R/M (e.g., backward move, jump, U-turn, etc.)
- Leverage the existing Strategy pattern and `ActionParser` registry for zero-modification extensibility

### V4 — Multi-Rover Control
- Support multiple rovers on the same plane
- Rover registry / fleet management
- Collision detection and coordination between rovers

### V5 — Real-Time UI
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

### V1 (Completed)
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

### V2–V5
_To be defined at implementation time._
