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

### V4 — Multi-Rover Control

**Goal:** Manage multiple rovers on a shared grid with unique IDs, collision avoidance between rovers, and support for both sequential and parallel execution modes.

#### Problem Analysis

Current system supports one rover. Multi-rover introduces:
1. **Identity** — each rover needs a unique ID for selection and command routing.
2. **Shared state** — rovers share the same grid; one rover's position is effectively a dynamic obstacle for others.
3. **Collision** — two rovers must not occupy the same cell. Unlike static obstacles, rover positions change every step.
4. **Execution ordering** — when multiple rovers have commands, the order of execution affects outcomes (especially collisions).

#### Strategy Comparison

**How to handle inter-rover collision detection?**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| Collision check in Arena orchestration | Arena checks target cell before delegating to Rover | Clear separation; Arena owns fleet state | Duplicates validation logic outside Rover | Rejected |
| Modify Rover to accept occupied set | Rover receives `Set<Position>` of other rovers | Rover handles everything | Couples Rover to fleet concept | Rejected |
| **ArenaEnvironment wrapper** | Wraps base Environment; adds other rovers' positions as dynamic obstacles | Rover unchanged; existing ConflictPolicy works for collisions; clean composition via Environment interface | Extra wrapper class | **Adopted** |

**Execution modes:**

| Mode | Description | Behavior |
|------|-------------|----------|
| **Sequential** | One rover completes all commands, then the next | Simple; deterministic; earlier rovers have "priority" |
| **Parallel (round-robin)** | One step per rover per round; all rovers advance together | Fair; more realistic; requires per-round collision resolution |

Both supported. Default: sequential. Parallel via `--parallel` flag.

**Parallel collision resolution:** If two rovers try to move to the same cell in the same round, neither moves (both treated as blocked, ConflictPolicy applied to each).

#### Design Discussion

- **ArenaEnvironment composition:** `ArenaEnvironment` wraps any `Environment` (including `UnboundedEnvironment` or `GridEnvironment`). It checks rover collisions first, then delegates to the base environment for boundary/obstacle checks. The `validate(current, proposed)` method uses `current` to identify the moving rover and excludes it from the occupied set.
- **Rover creation:** Arena assigns each rover an `ArenaEnvironment` that has a reference back to the Arena for dynamic position queries. Rovers are unaware they're in an arena.
- **CLI design:**
  ```
  # Define rovers inline: --rover "ID:x,y,direction:commands"
  java -jar rover.jar --arena --grid 5x5 \
    --rover "R1:0,0,N:MMRMM" \
    --rover "R2:4,4,S:MMLM"

  # Parallel execution
  java -jar rover.jar --arena --grid 5x5 --parallel \
    --rover "R1:0,0,N:MMRMM" \
    --rover "R2:4,4,S:MMLM"

  # With visual mode
  java -jar rover.jar --arena --grid 5x5 --visual --delay 500 \
    --rover "R1:0,0,N:MMRMM" \
    --rover "R2:4,4,S:MMLM"
  ```
- **Output format:** Non-visual mode prints each rover's final position: `R1:2,3 R2:3,1`. Visual mode renders all rovers on the same grid with distinct symbols (`A▲`, `B▲`, etc. or numbered).
- **Backward compatibility:** Without `--arena`, single-rover mode is unchanged.

#### Class & Data Structure Changes

##### `Arena` — Class (New)
Fleet manager. Owns rovers, shared environment, and execution orchestration.

| Member | Type | Description |
|--------|------|-------------|
| `rovers` | `Map<String, Rover>` | Rover registry keyed by ID |
| `roverPositions` | `Map<String, Position>` | Live position tracking for collision detection |
| `baseEnvironment` | `Environment` | Shared environment (grid/obstacles) |
| `conflictPolicy` | `ConflictPolicy` | Shared conflict policy |

| Method | Signature | Description |
|--------|-----------|-------------|
| `createRover` | `Rover createRover(String id, Position pos, Direction dir)` | Creates and registers a rover with ArenaEnvironment |
| `removeRover` | `void removeRover(String id)` | Removes a rover from the arena |
| `getRover` | `Rover getRover(String id)` | Retrieves a rover by ID |
| `getPositions` | `Map<String, Position> getPositions()` | Returns all rover positions |
| `executeSequential` | `void executeSequential(Map<String, List<Action>> commands)` | Executes each rover's commands in full, one rover at a time |
| `executeParallel` | `void executeParallel(Map<String, List<Action>> commands)` | Round-robin: one step per rover per round |
| `isOccupied` | `boolean isOccupied(Position pos, Position excludeSelf)` | Returns true if any rover (other than the one at excludeSelf) occupies pos |

##### `ArenaEnvironment` — Class implements `Environment` (New)
Wraps a base Environment and adds dynamic rover collision detection.

| Member | Type | Description |
|--------|------|-------------|
| `base` | `Environment` | Underlying environment (grid/obstacles/unbounded) |
| `arena` | `Arena` | Reference to arena for position queries |

| Method | Signature | Description |
|--------|-----------|-------------|
| `validate` | `MoveResult validate(Position current, Position proposed)` | Check rover collision first (via `arena.isOccupied`), then delegate to base |

##### `Rover` — Unchanged
No modifications needed. Collision is transparent through the ArenaEnvironment.

##### `App` — Modified
Add `--arena`, `--rover`, and `--parallel` CLI flags.

| Flag | Format | Description |
|------|--------|-------------|
| `--arena` | (no value) | Enable multi-rover arena mode |
| `--rover` | `"ID:x,y,dir:commands"` | Define a rover (repeatable) |
| `--parallel` | (no value) | Use round-robin parallel execution (default: sequential) |

#### Test Plan

| Dimension | Covers | Key Scenarios |
|-----------|--------|---------------|
| Rover lifecycle | Create, get, remove | Create rover with ID, duplicate ID rejected, remove rover, get non-existent ID |
| Collision avoidance | Rover blocked by another rover | Move into occupied cell; ConflictPolicy FAIL/SKIP/REVERSE all work for collisions |
| ArenaEnvironment | Collision + base environment | Collision check before boundary check; collision + obstacle; no collision = delegate to base |
| Sequential execution | One rover at a time | Two rovers, first completes before second starts; first rover's final position affects second |
| Parallel execution | Round-robin steps | Two rovers moving toward each other; mutual collision in same round; uneven command lengths |
| Combined constraints | Arena + grid + obstacles | Rovers on bounded grid with obstacles and collision avoidance simultaneously |
| Visual mode | Multi-rover rendering | All rovers shown on grid with distinct markers; paths for each rover |
| Backward compatibility | Single-rover mode unchanged | All V0–V5 tests pass without `--arena` |
| CLI parsing | `--arena`, `--rover`, `--parallel` | Multiple `--rover` flags, missing commands, invalid format |
| Concurrency | Thread safety in arena | Concurrent reads of arena state during execution |

### V5 — Real-Time UI

**Goal:** Enable step-by-step visual observation of rover movement in real time, starting with a terminal-based renderer. Split into two phases: V5a (Observer + step execution engine) and V5b (Terminal renderer).

#### Problem Analysis

Current `Rover.execute(List<Action>)` runs all actions synchronously and only returns the final state. There is no way for external components to observe intermediate states. Real-time UI requires:
1. **Per-step notification** — after each action, interested parties are informed of the state change.
2. **Paced execution** — configurable delay between steps so humans can follow the animation.
3. **Rendering** — visual representation of the grid, rover, path, and obstacles.

#### Strategy Comparison

**Notification mechanism:**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| Polling (UI queries Rover in a loop) | UI thread periodically reads `getState()` | Simple | Misses steps if execution is fast; wastes CPU if idle | Rejected |
| **Observer pattern** | Rover notifies registered listeners after each step | Push-based, no missed steps; clean decoupling | Slightly more complex; listener must not block execution | **Adopted** |
| Event queue / message bus | Actions publish events to a shared queue | Maximum decoupling | Over-engineered for single-rover CLI | Rejected |

**Paced execution:**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| Sleep inside `Rover.execute()` | Add `Thread.sleep()` between actions in Rover | Simple | Pollutes domain class with timing concerns; blocks synchronized lock during sleep | Rejected |
| **External StepExecutor** | Separate class feeds actions to Rover one at a time with configurable delay | Clean separation; Rover stays pure; delay is configurable | Extra class | **Adopted** |

**UI technology (MVP):**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| **Terminal (ANSI)** | Character-based grid drawn with ANSI escape codes | Zero dependencies; works everywhere; fast to implement | Limited visual fidelity | **Adopted for MVP** |
| Swing | JDK built-in GUI | Richer visuals | Heavier setup; not needed for MVP | Deferred |
| JavaFX | Modern GUI framework | Best visuals and animation | External dependency; not needed for MVP | Deferred |

#### Design Discussion

- **Listener thread safety:** Listeners are notified inside `Rover`'s synchronized `execute()` block, guaranteeing they see a consistent state. Listeners must not block (rendering should be fast or buffered).
- **StepExecutor threading:** Runs on a separate thread. Feeds one action at a time to Rover, then sleeps for the configured delay. The main thread can wait for completion or proceed.
- **Terminal rendering approach:** Uses ANSI escape codes to clear screen and redraw the grid on each step. Shows rover with directional arrow (`▲ ▼ ◄ ►`), path trail (`·`), obstacles (`#`), and step info.
- **`--visual` CLI flag:** Enables visual mode. Without it, behavior is unchanged (backward compatible). Visual mode implies a default step delay (e.g., 500ms) which can be overridden with `--delay <ms>`.
- **Grid size inference:** In visual mode without `--grid`, a default viewport (e.g., 20x20 centered on origin) is used for unbounded environments.
- **Layered architecture:** Observer and StepExecutor are pure backend (V5a) — no UI dependency. TerminalRenderer is a listener implementation (V5b). Future Swing/JavaFX renderers just implement the same listener interface.

#### Class & Data Structure Changes

##### V5a — Observer + Step Execution

###### `RoverEvent` — Record (New)
Immutable snapshot of a single step execution.

| Field | Type | Description |
|-------|------|-------------|
| `previousState` | `RoverState` | State before the action |
| `newState` | `RoverState` | State after the action |
| `action` | `Action` | The action that was executed |
| `stepIndex` | `int` | Zero-based index in the command sequence |
| `totalSteps` | `int` | Total number of actions in the sequence |
| `blocked` | `boolean` | Whether the move was blocked by environment |

###### `RoverListener` — Interface (New)
Observer contract for rover state changes.

| Method | Signature | Description |
|--------|-----------|-------------|
| `onStep` | `void onStep(RoverEvent event)` | Called after each action execution |
| `onComplete` | `void onComplete(RoverState finalState)` | Called when the entire sequence finishes |

###### `Rover` — Modified
Add listener support.

| Change | Detail |
|--------|--------|
| New field | `private final List<RoverListener> listeners` |
| New method | `void addListener(RoverListener)` |
| New method | `void removeListener(RoverListener)` |
| Modified `executeOne()` | After state update, notify all listeners with `RoverEvent` |

###### `StepExecutor` — Class (New)
Executes actions one at a time with configurable delay.

| Member | Type | Description |
|--------|------|-------------|
| `rover` | `Rover` | The rover to control |
| `delayMs` | `long` | Milliseconds between steps |

| Method | Signature | Description |
|--------|-----------|-------------|
| `execute` | `void execute(List<Action> actions)` | Feeds actions to rover one by one with delay |
| `executeAsync` | `Future<?> executeAsync(List<Action> actions)` | Same as above but on a background thread |

##### V5b — Terminal Renderer

###### `TerminalRenderer` — Class implements `RoverListener` (New)
Draws the grid and rover state to the terminal using ANSI escape codes.

| Member | Type | Description |
|--------|------|-------------|
| `width` | `int` | Viewport width in cells |
| `height` | `int` | Viewport height in cells |
| `obstacles` | `Set<Position>` | Obstacle positions to render |
| `path` | `List<Position>` | Accumulated rover path trail |

| Method | Signature | Description |
|--------|-----------|-------------|
| `onStep` | `void onStep(RoverEvent event)` | Clears screen, redraws grid with updated rover position and path |
| `onComplete` | `void onComplete(RoverState finalState)` | Renders final state with completion message |
| `render` | `void render(RoverState state, int step, int total)` | Core rendering logic |

**Rendering symbols:**

| Symbol | Meaning |
|--------|---------|
| `▲` `▼` `◄` `►` | Rover facing N/S/W/E |
| `·` | Path trail (visited cell) |
| `#` | Obstacle |
| `.` | Empty cell |

###### `App` — Modified
Add `--visual` and `--delay <ms>` CLI flags. When visual mode is active, use `StepExecutor` + `TerminalRenderer` instead of direct batch execution.

#### Test Plan

| Dimension | Covers | Key Scenarios |
|-----------|--------|---------------|
| Observer notification | Listener receives correct events | Single action, sequence, step index/total correct, blocked moves reported |
| Listener lifecycle | Add/remove listeners | Add multiple listeners, remove mid-sequence, no listeners (no-op) |
| StepExecutor timing | Actions executed with delay | Verify actions execute sequentially with approximate delay; async returns Future |
| StepExecutor + conflict | Policies work through StepExecutor | FAIL aborts (listener gets onComplete after error), SKIP/REVERSE continue |
| TerminalRenderer output | Correct grid rendering | Rover at various positions, directional arrows, path trail accumulation, obstacles rendered |
| Backward compatibility | Non-visual mode unchanged | All V0–V2 tests pass; no `--visual` = same output as before |
| CLI flags | `--visual` and `--delay` parsing | `--visual` alone, `--visual --delay 200`, `--visual` with grid/obstacles |

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

**Scope:** Manage multiple rovers on a shared grid via an `Arena` class. Each rover has a unique ID for selection and command routing. Collision avoidance is handled transparently through `ArenaEnvironment` (wraps base Environment + dynamic rover positions). Two execution modes: sequential (one rover at a time) and parallel (round-robin). CLI extended with `--arena`, `--rover "ID:x,y,dir:commands"`, and `--parallel` flags. Without `--arena`, single-rover mode unchanged.

- [ ] `ArenaEnvironment` — wraps base Environment, adds rover collision detection via `arena.isOccupied()`
- [ ] `Arena` — fleet manager: rover registry (`createRover`, `removeRover`, `getRover`), position tracking, `isOccupied()`
- [ ] `Arena.executeSequential()` — one rover completes all commands, then the next
- [ ] `Arena.executeParallel()` — round-robin: one step per rover per round, mutual collision = both blocked
- [ ] Modify `App` — add `--arena`, `--rover`, `--parallel` CLI flags
- [ ] Multi-rover visual rendering — distinct symbols per rover on shared grid
- [ ] Test suite: rover lifecycle, collision avoidance (all 3 ConflictPolicies), sequential/parallel execution, combined constraints (arena + grid + obstacles), visual mode, CLI parsing, backward compatibility
- [ ] Coverage verification (maintain 95%+ branch coverage)

### V5a — Observer + Step Execution

**Scope:** Add Observer pattern to Rover so external components can observe each step of execution. Introduce `RoverListener` interface, `RoverEvent` record, and `StepExecutor` for paced action execution with configurable delay. This is the pure backend foundation — no UI dependency. All existing behavior remains backward compatible (no listeners = no overhead).

- [ ] `RoverEvent` record (previousState, newState, action, stepIndex, totalSteps, blocked)
- [ ] `RoverListener` interface (`onStep`, `onComplete`)
- [ ] Modify `Rover`: add `listeners` list, `addListener()`, `removeListener()`, notify in `executeOne()`
- [ ] `StepExecutor`: synchronous `execute()` with configurable delay, async `executeAsync()` on background thread
- [ ] Test suite: observer notification, listener lifecycle, StepExecutor timing, conflict policy integration
- [ ] Coverage verification (maintain 95%+ branch coverage)

### V5b — Terminal Renderer

**Scope:** Implement `TerminalRenderer` as a `RoverListener` that draws the grid, rover, path trail, and obstacles using ANSI escape codes. Add `--visual` and `--delay <ms>` CLI flags to App. In visual mode, use `StepExecutor` + `TerminalRenderer` for animated step-by-step output. Without `--visual`, behavior is unchanged.

- [ ] `TerminalRenderer` implements `RoverListener`: ANSI-based grid rendering with directional arrows, path trail, obstacles
- [ ] Modify `App`: add `--visual` and `--delay <ms>` flags; wire StepExecutor + TerminalRenderer in visual mode
- [ ] Grid size inference for unbounded environments (default viewport centered on origin)
- [ ] Test suite: renderer output, CLI flag parsing, backward compatibility
- [ ] Coverage verification (maintain 95%+ branch coverage)
