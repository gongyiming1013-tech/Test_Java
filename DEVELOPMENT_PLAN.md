# Rover Control System — Development Plan

## Overview

A CLI system for controlling a robotic rover navigating a 2D grid. The rover starts at position (0,0) facing North and accepts commands: L (turn left 90°), R (turn right 90°), M (move forward). Output is the final position as `"x:y"`.

**Goals:** Build a production-ready, extensible rover control system that supports concurrent access, geographic constraints, multiple rovers, and eventually a real-time visual interface. The design prioritizes extensibility (new commands and behaviors added without modifying existing code) and thread safety for multi-user production environments.

## Design

### V0 + V1 (Current)

**Goal:** Build a single rover on an infinite plane with basic commands (L, R, M), then make it thread-safe for production multi-user concurrent access.

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

#### Strategy Comparisons

**V1 Concurrency — Hybrid `AtomicReference` + `synchronized`**

**Problem:** `Rover` has two mutable fields (`position`, `direction`) updated non-atomically. Under concurrent access, readers can observe inconsistent state (e.g., new position + old direction).

**Chosen approach:** Combine lock-free reads with mutually exclusive writes.

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

#### Test Plan

| Dimension | Covers | Key Scenarios |
|-----------|--------|---------------|
| Core functionality | Single action execution, sequence execution, full path traversal | Turn left/right, move forward, multi-step paths (MMRMM, MRMRMRMR square) |
| Edge cases | Empty/null input, custom initial state | Empty command string, null commands, rover starting at non-origin position |
| Error handling | Invalid command characters | Unregistered character at various positions in command string |
| Concurrency | Multi-thread write correctness, read/write consistency, parser thread safety | 16 threads × 1000 concurrent moves, concurrent readers during writes, concurrent register + parse on ActionParser |
| Integration | End-to-end CLI flow | `App.run()` produces correct `"x:y"` output for compound command strings |

---

### V2 — Geographic Constraints (Planned)

**Goal:** Constrain the rover to a finite grid with boundaries and obstacles, requiring move validation before state transitions.

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

#### Test Plan

| Dimension | Covers | Key Scenarios |
|-----------|--------|---------------|
| Boundary enforcement | _TBD_ | _e.g., move at grid edge, move beyond boundary_ |
| Obstacle handling | _TBD_ | _e.g., move into obstacle, obstacle at start position_ |
| Constraint + existing actions | _TBD_ | _e.g., sequences mixing turns and blocked moves_ |
| Error handling | _TBD_ | _e.g., invalid grid dimensions, obstacle outside grid_ |

---

### V3 — Additional Commands (Planned)

**Goal:** Expand the action set beyond L/R/M to support richer rover behaviors.

_Design to be added when this version enters planning._

### V4 — Multi-Rover Control (Planned)

**Goal:** Support multiple rovers operating concurrently on the same grid with collision awareness.

_Design to be added when this version enters planning._

### V5 — Real-Time UI (Planned)

**Goal:** Provide a visual interface for real-time observation and interactive control of rover movement.

_Design to be added when this version enters planning._

## Roadmap & Implementation

### V0 (MVP) — Completed

**Scope:** Implement a single rover navigating an infinite 2D plane with three basic commands (L, R, M). Establish the core domain model with Strategy pattern for extensible actions, immutable state via records, and registry-based command parsing. Full TDD with 95%+ branch coverage.

- [x] `Direction` enum (leaf — no deps)
- [x] `Position` record (depends on Direction)
- [x] `RoverState` record (pure data carrier)
- [x] `Action` interface + `TurnLeftAction` / `TurnRightAction` / `MoveForwardAction`
- [x] `InvalidActionException` + `ActionParser`
- [x] `Rover`
- [x] `App` CLI integration
- [x] Full TDD with 95%+ branch coverage

### V1 — Concurrency — Completed

**Scope:** Make Rover and ActionParser thread-safe for production multi-user scenarios. Adopt a hybrid concurrency strategy — `AtomicReference` for lock-free reads, `synchronized` for exclusive writes — to balance read throughput with safe action execution. See [Design > V0 + V1](#v0--v1-current) for strategy details.

- [x] Refactor `Rover`: merge `position` + `direction` into `AtomicReference<RoverState>`
- [x] `execute()` methods add `synchronized` — action executes exactly once
- [x] `getPosition()` / `getDirection()` delegate to `AtomicReference.get()` — lock-free reads
- [x] Add `getState()` method — returns immutable `RoverState` snapshot
- [x] `execute(List<Action>)` holds lock for entire batch — atomic sequence execution
- [x] `ActionParser`: `HashMap` → `ConcurrentHashMap` for thread-safe register + parse
- [x] Add `RoverConcurrencyTest` (4 tests: move correctness, state consistency, rotation invariant, parser thread safety)
- [x] Coverage verification (maintain 95%+ branch coverage)

### V2 — Geographic Constraints

**Scope:** Introduce a finite grid with defined width and height so the rover cannot move beyond edges. Support obstacles on specific cells that block movement. Define and implement a constraint violation strategy (e.g., ignore move, throw exception, or find alternative path).

- [ ] Grid boundaries: introduce a finite grid (`width × height`); rover cannot move beyond edges
- [ ] Obstacles: certain cells are impassable; rover must detect and handle blocked moves
- [ ] Constraint violation strategy: define how rover reacts (ignore move, throw exception, or find alternative path)
- [ ] Test suite for boundary and obstacle scenarios
- [ ] Coverage verification

### V3 — Additional Commands

**Scope:** Expand the action set beyond L/R/M to support richer rover behaviors (e.g., backward move, jump, U-turn). Leverage the existing Strategy pattern and ActionParser registry so new commands are added without modifying existing code.

- [ ] New actions (e.g., backward move, jump, U-turn — TBD)
- [ ] Leverage existing Strategy pattern + `ActionParser` registry for zero-modification extensibility
- [ ] Test suite for new actions
- [ ] Coverage verification

### V4 — Multi-Rover Control

**Scope:** Support multiple rovers operating concurrently on the same grid. Introduce a rover registry for fleet management and implement collision detection and coordination between rovers.

- [ ] Rover registry / fleet management
- [ ] Collision detection and coordination between rovers
- [ ] Test suite for multi-rover scenarios
- [ ] Coverage verification

### V5 — Real-Time UI

**Scope:** Provide a visual interface that renders rover position and movement in real time, replacing text-only coordinate output. Include live action execution rendering and an interactive control panel.

- [ ] Visual interface showing rover position and movement in real time
- [ ] Live rendering of action execution (not just final coordinates)
- [ ] Interactive control panel
