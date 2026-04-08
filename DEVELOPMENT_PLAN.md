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

**Goal:** Allow users to optionally constrain the rover to a finite grid with boundaries and obstacles. Default behavior (no constraints) remains identical to V1.

#### Strategy Comparison

**Where to enforce constraints?**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| Inside `Action.execute()` | Each action checks constraints before computing new state | Constraint logic co-located with movement | Violates SRP — actions shouldn't know about environment; every action must duplicate validation logic | Rejected |
| Inside `Rover.execute()` | Rover validates the new position after action computes it, before committing state | Centralized, single validation point; actions stay pure | Rover gains extra responsibility; but it's the state owner so this is natural | **Adopted** |
| Separate `MoveValidator` | External validator wraps action execution | Maximum decoupling | Over-engineered for current scope; adds indirection without clear benefit | Rejected |

**How to handle conflicts (boundary violations & obstacle collisions)?**

Three conflict resolution policies, applied uniformly to both boundary violations and obstacle collisions:

| Policy | CLI Flag | Behavior | Use Case |
|--------|----------|----------|----------|
| **FAIL** (default) | `--on-conflict fail` or omit | Rover stays put; throws `MoveBlockedException`; entire command sequence aborts | Safety-critical — caller must know something went wrong |
| **SKIP** | `--on-conflict skip` | Rover stays put; silently skips the blocked move; continues executing remaining commands | Fault-tolerant — rover does its best to complete the sequence |
| **REVERSE** | `--on-conflict reverse` | Rover turns 180° (reverses direction) instead of moving into the blocked cell; continues executing remaining commands | Autonomous exploration — rover bounces off walls and obstacles |

**Boundary mode** (orthogonal to conflict policy):

| Mode | CLI Flag | Behavior |
|------|----------|----------|
| **Bounded** (default) | `--grid WxH` without `--wrap` | Grid has hard edges; moving beyond triggers conflict policy |
| **Wrap** (toroidal) | `--grid WxH --wrap` | Position wraps to opposite edge; no conflict triggered for boundaries (obstacles still trigger conflict policy) |

#### Design Discussion

- **Backward compatibility:** `Environment` is optional. `Rover()` and `Rover(position, direction)` constructors remain unchanged and behave as V1 (no constraints). New constructor `Rover(position, direction, environment)` enables constraints.
- **Grid coordinate system:** Grid spans `(0,0)` to `(width-1, height-1)`. Position `(0,0)` is bottom-left corner.
- **Obstacle placement validation:** Obstacles must be within grid bounds. Placing an obstacle at the rover's start position is allowed (user error) but will block the first move.
- **Turn actions unaffected:** L and R don't change position, so they never trigger constraint validation. Only `MoveForwardAction` (and future movement actions) can be blocked.
- **CLI input design:**
  ```
  # No constraints (V1 compatible)
  java -jar rover.jar "MMRMM"

  # Grid with default conflict policy (FAIL)
  java -jar rover.jar --grid 10x10 "MMRMM"

  # Grid + wrap-around (boundaries wrap, obstacles still trigger conflict policy)
  java -jar rover.jar --grid 10x10 --wrap "MMRMM"

  # Grid + skip on conflict (blocked moves are silently skipped)
  java -jar rover.jar --grid 10x10 --on-conflict skip "MMRMM"

  # Grid + reverse on conflict (rover turns 180° at walls/obstacles)
  java -jar rover.jar --grid 10x10 --on-conflict reverse "MMRMM"

  # Grid + wrap + obstacles + skip
  java -jar rover.jar --grid 10x10 --wrap --obstacles "1,2;3,4" --on-conflict skip "MMRMM"
  ```
  All flags are optional. `--obstacles` requires `--grid`. `--on-conflict` defaults to `fail`. `--wrap` only affects boundary handling (obstacles always trigger the conflict policy).

#### Class & Data Structure Changes

##### `ConflictPolicy` — Enum (New)
Defines how the rover reacts when a move is blocked (by boundary or obstacle).

| Constant | Behavior |
|----------|----------|
| `FAIL`   | Rover stays put; throws `MoveBlockedException`; command sequence aborts |
| `SKIP`   | Rover stays put; move silently skipped; continues remaining commands |
| `REVERSE`| Rover turns 180° (direction reverses); stays at current position; continues remaining commands |

##### `BoundaryMode` — Enum (New)
Defines how grid edges are treated.

| Constant | Behavior |
|----------|----------|
| `BOUNDED` | Moving beyond edge triggers the `ConflictPolicy` |
| `WRAP`    | Position wraps to opposite edge (toroidal); no conflict triggered |

##### `Environment` — Interface (New)
Contract for move validation. Decouples constraint logic from Rover.

| Method | Signature | Description |
|--------|-----------|-------------|
| `validate` | `MoveResult validate(Position current, Position proposed)` | Returns `MoveResult` indicating the accepted position and whether a conflict occurred |

##### `MoveResult` — Record (New)
Result of an environment validation check.

| Field | Type | Description |
|-------|------|-------------|
| `position` | `Position` | The accepted position (original if no conflict, wrapped if WRAP mode) |
| `blocked` | `boolean` | `true` if the move was blocked (boundary in BOUNDED mode, or obstacle) |

##### `UnboundedEnvironment` — Class implements `Environment` (New)
No constraints. Always returns `MoveResult(proposed, false)`. Used as default when no grid is specified.

##### `GridEnvironment` — Class implements `Environment` (New)
Finite grid with optional obstacles and configurable boundary mode.

| Member | Type | Description |
|--------|------|-------------|
| `width` | `int` | Grid width (x range: 0 to width-1) |
| `height` | `int` | Grid height (y range: 0 to height-1) |
| `obstacles` | `Set<Position>` | Immutable set of blocked positions |
| `boundaryMode` | `BoundaryMode` | How grid edges are treated |

| Method | Signature | Description |
|--------|-----------|-------------|
| `validate` | `MoveResult validate(Position current, Position proposed)` | Checks obstacle → checks bounds (wrap or block) → returns `MoveResult` |

##### `MoveBlockedException` — Class extends `RuntimeException` (New)
Thrown when `ConflictPolicy.FAIL` is active and a move is blocked. Includes the blocked position and reason (boundary or obstacle).

##### `Rover` — Modified
Add optional `Environment` and `ConflictPolicy` support.

| Change | Detail |
|--------|--------|
| New fields | `private final Environment environment`, `private final ConflictPolicy conflictPolicy` |
| New constructor | `Rover(Position, Direction, Environment, ConflictPolicy)` |
| Modified `execute()` | After action computes new position: (1) call `environment.validate()`, (2) if blocked, apply `ConflictPolicy` — FAIL: throw; SKIP: keep current state; REVERSE: flip direction |
| Existing constructors | Unchanged — default to `UnboundedEnvironment` + `ConflictPolicy.FAIL` |

##### `App` — Modified
Parse new CLI flags and construct appropriate `Environment` and `ConflictPolicy`.

| Flag | Format | Description |
|------|--------|-------------|
| `--grid` | `WxH` (e.g., `10x10`) | Enable finite grid with given dimensions |
| `--wrap` | (no value) | Use `BoundaryMode.WRAP` instead of `BOUNDED` |
| `--obstacles` | `"x1,y1;x2,y2;..."` | Semicolon-separated obstacle positions |
| `--on-conflict` | `fail` / `skip` / `reverse` | Conflict resolution policy (default: `fail`) |

#### Test Plan

| Dimension | Covers | Key Scenarios |
|-----------|--------|---------------|
| Boundary — BOUNDED | Rover blocked at grid edge | Move north at top edge, move east at right edge, move at corner (two edges), interior moves unaffected |
| Boundary — WRAP | Toroidal wrap-around | Wrap north→south, wrap east→west, wrap at corner, full traversal wrapping multiple times |
| Obstacles | Blocked by obstacle | Move into obstacle, obstacle adjacent but not in path, multiple obstacles, obstacle at (0,0) |
| ConflictPolicy — FAIL | Sequence aborts on conflict | `MMMM` where 3rd move hits wall → exception, only first 2 moves committed |
| ConflictPolicy — SKIP | Blocked move skipped, rest continues | `MMMM` where 3rd move hits wall → skipped, 4th move also hits wall → skipped, turns still execute |
| ConflictPolicy — REVERSE | Rover flips direction on conflict | Move into wall → direction reverses + stays put, next M goes opposite way; move into obstacle → same |
| WRAP + obstacle + SKIP | Boundaries wrap but obstacles skip | Wrap at edge works, obstacle in path skipped, rest continues |
| Default / backward compat | No environment = V1 behavior | All V0/V1 tests pass unchanged with default constructors |
| Error handling | Invalid configuration | Zero/negative grid dimensions, obstacle outside grid bounds, invalid `--on-conflict` value |
| CLI integration | End-to-end with all flags | `--grid 5x5 "MMMMMM"`, `--grid 3x3 --wrap`, `--on-conflict skip`, `--obstacles` parsing, flag combinations |
| Concurrency | Thread safety with environment | Concurrent moves on bounded grid, concurrent reads during blocked moves, concurrent SKIP/REVERSE |

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

**Scope:** Allow users to optionally specify a finite grid (`width × height`), obstacle positions, boundary mode (bounded or wrap), and conflict resolution policy (fail, skip, or reverse). Rover validates moves against environment constraints before committing state. All constraints are optional — omitting them preserves V1 behavior (infinite plane, no obstacles). CLI extended with `--grid`, `--wrap`, `--obstacles`, and `--on-conflict` flags.

- [ ] `ConflictPolicy` enum (`FAIL`, `SKIP`, `REVERSE`)
- [ ] `BoundaryMode` enum (`BOUNDED`, `WRAP`)
- [ ] `MoveResult` record (`Position position`, `boolean blocked`)
- [ ] `Environment` interface with `MoveResult validate(Position current, Position proposed)` method
- [ ] `UnboundedEnvironment` — no constraints, default for backward compatibility
- [ ] `GridEnvironment` — finite grid with width/height, `Set<Position>` obstacles, configurable `BoundaryMode`
- [ ] `MoveBlockedException` — thrown when `ConflictPolicy.FAIL` is active and move is blocked
- [ ] Modify `Rover` — add optional `Environment` + `ConflictPolicy` fields; apply policy in `execute()` when move is blocked
- [ ] Modify `App` — parse `--grid`, `--wrap`, `--obstacles`, `--on-conflict` CLI flags
- [ ] Test suite: boundary BOUNDED/WRAP, obstacles, three conflict policies (FAIL/SKIP/REVERSE), backward compatibility, mixed sequences, error handling, CLI integration, concurrency
- [ ] Coverage verification (maintain 95%+ branch coverage)

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
