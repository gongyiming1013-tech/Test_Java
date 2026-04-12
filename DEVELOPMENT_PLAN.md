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

### V3 — Additional Commands

**Goal:** Add new movement commands (B, S, U), undo/redo functionality via command history, and verbose batch reporting.

#### New Commands

| Command | Class | Behavior |
|---------|-------|----------|
| `B` | `BackwardAction` | Move one step opposite to current facing direction; direction unchanged |
| `S` | `SpeedBoostAction` | Move two steps forward in current direction (only final position validated — behaves as a "jump") |
| `U` | `UTurnAction` | Reverse direction 180° (equivalent to two left turns); position unchanged |
| `Z` | `UndoAction` | Restore the state before the previous action (pop from history) |
| `Y` | `RedoAction` | Re-apply a previously undone action (pop from redo stack) |

All new commands are registered in `ActionParser` by default and work with existing `ConflictPolicy`, `Environment`, and `Arena`.

#### Strategy Comparison

**How to implement undo/redo?**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| State history in Rover | Rover stores a stack of previous `RoverState` snapshots; undo pops, redo re-pushes | Simple; `RoverState` is already immutable; O(1) undo/redo | Memory grows with history length | **Adopted** |
| Command Pattern with inverse | Each Action defines an `undo()` method that computes the inverse | No state storage | Some actions aren't trivially reversible (SpeedBoost across a wrap boundary); complex to implement | Rejected |
| External UndoManager | Separate class manages history outside Rover | Maximum decoupling | Adds coordination complexity between Rover and manager | Rejected |

**How do undo/redo fit the Action interface?**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| Separate `undo()`/`redo()` methods on Rover only (not in command string) | Undo/redo only available programmatically | Clean separation | Can't use Z/Y in command strings like "MMZRM" | Rejected |
| **Marker Actions** | `UndoAction`/`RedoAction` implement `Action`; Rover detects them via `instanceof` in `executeOne()` and handles specially | Seamless in command strings; ActionParser registers Z/Y normally | instanceof check in Rover | **Adopted** |

#### Design Discussion

- **Undo/redo history stack:** Rover gains two fields: `Deque<RoverState> history` and `Deque<RoverState> redoStack`. Before each normal action, current state is pushed to history. Undo pops history, pushes current to redo. Any non-undo/redo action clears the redo stack (standard behavior).
- **SpeedBoost validation:** Only the final position (current + 2*direction) is validated by Environment. The intermediate cell is NOT checked — SpeedBoost acts as a "jump". This gives it distinct behavior from executing M twice.
- **BackwardAction vs U+M:** `B` moves backward without turning. `U` then `M` turns 180° then moves forward — the rover ends up facing the opposite direction. `B` preserves facing.
- **Undo with listeners:** When undo is executed, listeners receive an `onStep` event with the undo action and the restored state. The `blocked` field is always `false` for undo/redo.
- **Undo limits:** Undo goes back to the initial state at most. Undoing with empty history is a no-op (skipped).
- **Verbose mode (`--verbose`):** Text-only step reporting without the grid visual. Prints each step's position and action on a single line.

#### Class & Data Structure Changes

##### `BackwardAction` — Class implements `Action` (New)
Moves one step opposite to current direction; facing unchanged.

##### `SpeedBoostAction` — Class implements `Action` (New)
Moves two steps forward in current direction (jump).

##### `UTurnAction` — Class implements `Action` (New)
Reverses direction 180°; position unchanged. Uses `Direction.reverse()`.

##### `UndoAction` — Class implements `Action` (New)
Marker action. `execute()` throws `UnsupportedOperationException` — never called directly. Rover detects via `instanceof` and pops from history.

##### `RedoAction` — Class implements `Action` (New)
Marker action. Same as UndoAction — Rover detects and pushes from redo stack.

##### `ActionParser` — Modified
Register new defaults: `B`→BackwardAction, `S`→SpeedBoostAction, `U`→UTurnAction, `Z`→UndoAction, `Y`→RedoAction.

##### `Rover` — Modified
Add undo/redo state management.

| Change | Detail |
|--------|--------|
| New fields | `Deque<RoverState> history`, `Deque<RoverState> redoStack` |
| New methods | `void undo()`, `void redo()` (internal, called by executeOne) |
| Modified `executeOne()` | Detect UndoAction/RedoAction via instanceof; push to history before normal actions; clear redoStack on normal actions |

##### `App` — Modified
Add `--verbose` flag for text-only step reporting.

#### Test Plan

| Dimension | Covers | Key Scenarios |
|-----------|--------|---------------|
| BackwardAction | Move opposite to facing | Backward from each direction; facing preserved; with environment constraints |
| SpeedBoostAction | Jump two cells forward | Boost in each direction; boost past obstacle (jump over); boost into boundary |
| UTurnAction | Reverse 180° | U-turn from each direction; position unchanged |
| Undo — basic | Restore previous state | Undo single move, undo turn, undo multiple times back to origin |
| Undo — edge cases | Empty history, undo at start | Undo with no history is no-op; undo then redo restores |
| Redo — basic | Re-apply undone action | Redo after undo; multiple redo; redo stack cleared by new action |
| Redo — edge cases | Redo without prior undo | Redo with empty stack is no-op |
| Mixed commands | Combined in command string | "MMZRM" (move, move, undo, turn, move); "MMBSUZ" complex sequences |
| Environment integration | Undo/redo with grid/obstacles | Undo a blocked move; redo into a now-occupied cell (arena) |
| Listener integration | Observer receives undo/redo events | onStep called for undo with correct previous/new state |
| Backward compatibility | Existing commands unchanged | All V0–V5 tests pass; B/S/U/Z/Y are additive only |
| Verbose mode | Text-only step reporting | `--verbose` prints each step without grid visual |

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

**Goal:** Enable step-by-step visual observation of rover movement in real time, starting with a terminal-based renderer and then polishing it into a modern CLI experience. Split into three phases: V5a (Observer + step execution engine), V5b (Terminal renderer MVP), and V5c (Enhanced UI with theming, color, box-drawing, status dashboard, and gradient animations).

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

##### V5c — Enhanced Terminal UI

###### Problem Analysis

V5b's `TerminalRenderer` produces a functional but bare-bones display: monochrome text, no visual frame, no status feedback, and uniform path dots. This is adequate for debugging but falls short of a polished, "modern CLI" experience. V5c addresses three gaps:

1. **Visual polish** — color, box-drawing borders, and directional symbols make the grid instantly readable.
2. **Contextual feedback** — a status dashboard shows what's happening (current step, command preview, progress) without the user having to decode raw output.
3. **Animation quality** — gradient path trails and flicker-free rendering produce smooth, pleasant animations.

###### Strategy Comparison

**Color approach:**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| No color (V5b status quo) | Monochrome symbols only | Maximum terminal compatibility | Hard to distinguish elements at a glance | Baseline |
| ANSI 16-color | Standard 8 colors + bold variants | Supported everywhere | Very limited palette; gradient trail impossible | Rejected |
| **ANSI 256-color** | 6×6×6 color cube + 24 grayscale | Broad support (virtually all modern terminals); sufficient for gradient trail | Not available on very old terminals | **Adopted** |
| True color (24-bit) | Full RGB | Unlimited palette | Patchy support; overkill for grid rendering | Rejected |

**Flicker-free rendering:**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| Clear screen + full redraw (V5b) | `\033[2J` then print | Simple | Full screen flash between frames; visible flicker | Baseline |
| **Cursor home + overwrite** | `\033[H` then overwrite in place | No flicker; terminal only updates changed characters | Lines must be same length (pad with spaces) | **Adopted** |
| Double buffering (off-screen string) | Build full frame in `StringBuilder`, flush once | Minimal I/O calls | Still needs cursor positioning; marginal benefit over home+overwrite for terminal | Considered — combine with cursor-home |

**Theming architecture:**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| Hardcoded colors in renderer | Color codes inline in `TerminalRenderer` | Fast to implement | Can't swap looks; violates OCP | Rejected |
| **Theme interface + implementations** | `Theme` defines color/symbol mappings; concrete themes provide values | Swappable; testable; future themes trivial to add | Extra interface + classes | **Adopted** |
| External config file (JSON/YAML) | Theme loaded from disk | Most flexible | Over-engineered for CLI tool; adds parsing dependency | Rejected |

###### Design Discussion

- **Composition with V5b:** V5c does **not** replace `TerminalRenderer` — it enhances it. `TerminalRenderer` is refactored to delegate visual concerns to `Theme`, `GridFrame`, and `StatusBar`. The `RoverListener` contract stays identical; this is purely an internal rendering upgrade.
- **Gradient path trail:** The path list is rendered with a color gradient — the most recent N positions are bright (e.g., cyan → blue), older ones dim (gray). This gives an intuitive sense of movement direction and recency. The gradient window size (N) is configurable via `Theme`.
- **Box-drawing grid frame:** The grid is enclosed in Unicode box-drawing characters (`─ │ ┌ ┐ └ ┘ ┬ ┴ ├ ┤ ┼`) with axis labels (column/row numbers). This clearly delineates the playing field and looks significantly more polished.
- **Status bar layout:** Rendered below the grid frame, the status bar shows:
  ```
  Step 3/10  ████░░░░░░  Command: M M R [M] M L M M R M
  Rover: (2,3) facing NORTH     Status: Moving...
  ```
  The command sequence highlights the current action in brackets. A progress bar visualizes completion. On blocked moves, status changes to `⚠ Blocked (obstacle)` in yellow/red.
- **Blocked-move visual feedback:** When a move is blocked, the rover symbol briefly renders in red (or a warning color from theme) for one frame before continuing. This gives the user immediate visual feedback without breaking the animation flow.
- **Multi-rover color coding (V4 integration):** Each rover in arena mode gets a unique color from the theme's rover palette. Path trails are colored per rover. This makes it trivial to follow individual rovers in parallel execution mode.
- **`--theme` CLI flag:** Optional. Selects a named theme. Default: `modern`. Available: `modern` (colorful, box-drawing), `minimal` (subtle colors, thin frame), `mono` (no color, V5b-compatible).
- **Graceful degradation:** If the terminal doesn't support 256-color (detected via `TERM` environment variable or `--theme mono`), fall back to the `mono` theme automatically. No crash, no garbled output.

###### Class & Data Structure Changes

**`AnsiStyle`** — Utility Class (New)
Encapsulates ANSI escape sequence generation. All color and style logic lives here — no raw escape codes elsewhere in the codebase.

| Method | Signature | Description |
|--------|-----------|-------------|
| `fg256` | `static String fg256(int code)` | Foreground color from 256-color palette |
| `bg256` | `static String bg256(int code)` | Background color from 256-color palette |
| `bold` | `static String bold()` | Bold text |
| `dim` | `static String dim()` | Dim/faint text |
| `reset` | `static String reset()` | Reset all styles |
| `cursorHome` | `static String cursorHome()` | Move cursor to top-left (`\033[H`) |
| `hideCursor` | `static String hideCursor()` | Hide cursor during animation |
| `showCursor` | `static String showCursor()` | Restore cursor on completion |

**`Theme`** — Interface (New)
Defines the full visual vocabulary for rendering. Implementations supply colors, symbols, and layout parameters.

| Method | Signature | Description |
|--------|-----------|-------------|
| `roverColor` | `String roverColor(int roverIndex)` | ANSI color for the nth rover (multi-rover) |
| `roverSymbol` | `String roverSymbol(Direction dir)` | Directional symbol (`▲ ▼ ◄ ►`) |
| `pathColor` | `String pathColor(int age, int maxAge)` | Gradient color for path trail; `age` = steps since visited |
| `pathSymbol` | `String pathSymbol()` | Trail symbol (e.g., `·` or `•`) |
| `obstacleColor` | `String obstacleColor()` | Color for obstacle cells |
| `obstacleSymbol` | `String obstacleSymbol()` | Obstacle symbol (e.g., `█` or `#`) |
| `emptySymbol` | `String emptySymbol()` | Empty cell symbol (e.g., `·` or ` `) |
| `borderColor` | `String borderColor()` | Color for grid frame |
| `blockedColor` | `String blockedColor()` | Highlight color for blocked-move flash |
| `statusStyle` | `String statusStyle()` | Color/style for status bar text |
| `gradientWindow` | `int gradientWindow()` | Number of recent steps for gradient trail |

**`ModernTheme`** — Class implements `Theme` (New)
Default colorful theme. Cyan rover, blue→gray gradient trail, white box-drawing frame, red blocked flash, green progress bar.

**`MinimalTheme`** — Class implements `Theme` (New)
Subtle colors. White rover, gray gradient trail, dim frame, yellow blocked flash. For users who prefer understated visuals.

**`MonoTheme`** — Class implements `Theme` (New)
No color. Plain ASCII symbols. Equivalent to V5b output. Fallback for unsupported terminals.

**`GridFrame`** — Class (New)
Renders the box-drawing border and axis labels around the grid.

| Member | Type | Description |
|--------|------|-------------|
| `width` | `int` | Grid width in cells |
| `height` | `int` | Grid height in cells |
| `theme` | `Theme` | Visual theme for border colors |

| Method | Signature | Description |
|--------|-----------|-------------|
| `renderTop` | `String renderTop()` | Top border with column numbers: `┌──0──1──2──┐` |
| `renderRow` | `String renderRow(int y, String[] cells)` | One grid row with side borders: `│ · ▲ · │ 2` |
| `renderBottom` | `String renderBottom()` | Bottom border: `└──────────┘` |

**`StatusBar`** — Class (New)
Renders the informational panel below the grid.

| Member | Type | Description |
|--------|------|-------------|
| `theme` | `Theme` | Visual theme for status styling |

| Method | Signature | Description |
|--------|-----------|-------------|
| `render` | `String render(RoverEvent event, String commandSequence)` | Full status bar: progress bar, current command highlight, rover state, status message |
| `progressBar` | `String progressBar(int current, int total, int width)` | `████░░░░░░` style bar |
| `commandPreview` | `String commandPreview(String commands, int currentIndex)` | Highlights current command in sequence |

**`TerminalRenderer`** — Modified
Refactored to compose with `Theme`, `GridFrame`, and `StatusBar`.

| Change | Detail |
|--------|--------|
| New field | `private final Theme theme` |
| New field | `private final GridFrame gridFrame` |
| New field | `private final StatusBar statusBar` |
| Modified `render()` | Uses `cursorHome` + overwrite instead of clear screen; delegates to `gridFrame` for borders, `theme` for colors/symbols, `statusBar` for dashboard |
| Modified `onStep()` | Applies gradient to path trail using `theme.pathColor(age, maxAge)`; renders blocked flash via `theme.blockedColor()` |
| Modified `onComplete()` | Shows cursor, renders final summary |

**`App`** — Modified
Add `--theme` CLI flag.

| Flag | Format | Description |
|------|--------|-------------|
| `--theme` | `modern` / `minimal` / `mono` | Selects visual theme (default: `modern`; auto-fallback to `mono` if terminal lacks 256-color support) |

###### Test Plan

| Dimension | Covers | Key Scenarios |
|-----------|--------|---------------|
| AnsiStyle | Escape sequence generation | `fg256` produces correct codes, `reset` clears all, `cursorHome` outputs `\033[H` |
| Theme contract | All three themes implement interface correctly | Each method returns non-null; `pathColor` gradient varies with age; `roverColor` cycles for multi-rover |
| GridFrame rendering | Box-drawing output | Correct top/bottom borders, row rendering with side borders, axis labels, various grid sizes |
| StatusBar rendering | Dashboard content | Progress bar at 0%/50%/100%, command preview highlights correct index, blocked status message |
| Gradient path trail | Color varies by recency | Most recent step is brightest, oldest step in window is dimmest, beyond window is uniform dim |
| Flicker-free rendering | Cursor-home overwrite | Output starts with `\033[H`, no `\033[2J` (clear screen), line lengths are padded consistently |
| Blocked-move feedback | Visual flash on conflict | Blocked move renders rover in `blockedColor`, non-blocked move renders in `roverColor` |
| Theme selection | `--theme` flag and auto-detect | `--theme modern`, `--theme mono`, invalid theme name rejected, auto-fallback when `TERM=dumb` |
| Multi-rover colors | Per-rover color assignment | Two rovers get different colors; three+ rovers cycle palette; arena mode renders colored trails |
| MonoTheme compatibility | V5b-equivalent output | `MonoTheme` produces output with no ANSI color codes; symbols match V5b spec |
| Backward compatibility | Non-visual mode unchanged | All V0–V4 tests pass; `--visual` without `--theme` defaults to `modern` |

#### Test Plan (V5 Combined)

| Dimension | Covers | Key Scenarios |
|-----------|--------|---------------|
| Observer notification | Listener receives correct events | Single action, sequence, step index/total correct, blocked moves reported |
| Listener lifecycle | Add/remove listeners | Add multiple listeners, remove mid-sequence, no listeners (no-op) |
| StepExecutor timing | Actions executed with delay | Verify actions execute sequentially with approximate delay; async returns Future |
| StepExecutor + conflict | Policies work through StepExecutor | FAIL aborts (listener gets onComplete after error), SKIP/REVERSE continue |
| TerminalRenderer output | Correct grid rendering | Rover at various positions, directional arrows, path trail accumulation, obstacles rendered |
| Enhanced rendering (V5c) | Color, frame, status, gradient | AnsiStyle codes, GridFrame borders, StatusBar dashboard, gradient trail, blocked flash, theme selection |
| Backward compatibility | Non-visual mode unchanged | All V0–V4 tests pass; no `--visual` = same output as before |
| CLI flags | `--visual`, `--delay`, `--theme` parsing | `--visual` alone, `--visual --delay 200`, `--theme modern/minimal/mono`, `--visual` with grid/obstacles |

---

### V6 — Interactive Web UI (Planned)

**Goal:** Replace CLI-driven configuration with an interactive browser-based UI. Users launch a local web server (`java -jar rover.jar --web`), open `http://localhost:8080`, and configure grids, obstacles, rovers, and commands through point-and-click controls. Execution streams back to the browser in real time. The architecture is designed so that future server deployment with multi-user sessions is a natural extension, not a rewrite.

Split into four phases:
- **V6a** — Web server + REST API + static frontend (config + synchronous run).
- **V6b** — Real-time event streaming via Server-Sent Events (SSE), animated Canvas rendering.
- **V6c** — Interactive editing: click-to-place obstacles, click-to-add rovers, pause/resume/step controls, theme switching.
- **V6d** — Session isolation groundwork: each browser has its own independent arena; TTL-based cleanup. (Foundation for future multi-user shared rooms in V7.)

#### Problem Analysis

The V5 CLI is powerful but unwelcoming for non-technical users:
1. **Configuration ergonomics** — grid size, obstacles, and multi-rover specs must be encoded as fragile flag strings (`--rover "R1:0,0,N:MMRMM"`). Users must memorize the format, escape quotes, and iterate by retyping entire commands.
2. **Discoverability** — there is no way to visually see an empty grid and *place* an obstacle at coordinate (3, 4). Users must mentally translate spatial intent into flag syntax.
3. **Iteration speed** — to tweak a rover's starting direction or add one more obstacle, the user rebuilds the full command and re-runs from scratch.
4. **Future multi-user** — any further UI investment should be on a platform (web) that generalizes to server deployment without rewrite. CLI + terminal UI cannot become multi-user without abandoning the UI layer entirely.

A browser-based UI solves all four: visual grid editing, click-to-configure, sub-second iteration via fetch/run cycle, and the HTTP server layer doubles as the future multi-user backend.

#### Strategy Comparison

**UI platform:**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| Terminal TUI (Lanterna/JLine) | Interactive text-mode UI with forms | Stays in terminal; lightweight | Limited interaction (no true click/drag); no multi-user path | Rejected |
| JavaFX / Swing desktop app | Native GUI window launched from JAR | Rich controls; no browser needed | Desktop-only; multi-user requires full rewrite with separate network layer | Rejected |
| **Web UI (HTTP server + browser)** | Embedded HTTP server serves static assets + REST/SSE; browser renders | Natural path to multi-user; rich rendering via Canvas/SVG; zero install for end users; localhost works offline | Extra components (server + frontend); small dependency footprint | **Adopted** |

**HTTP framework:**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| JDK `com.sun.net.httpserver` | Built-in HTTP server | Zero dependencies | Very low-level; no routing, no SSE helpers, no middleware; awkward for anything beyond toy examples | Rejected |
| Spark Java | Minimal micro-framework | Small API | Stagnant maintenance; weaker SSE/WebSocket story | Rejected |
| Jetty standalone | Servlet container | Battle-tested | Heavier; verbose config | Rejected |
| **Javalin** | Modern Kotlin/Java micro-framework on top of Jetty | Clean API; built-in SSE and WebSocket; active maintenance (v6.x); small footprint (~700KB with transitive deps); production-grade (Jetty underneath) | One new dependency | **Adopted** |

**Real-time event transport:**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| Polling | Browser fetches `/state` on a timer | Simplest | Laggy; wastes bandwidth; missed frames on fast execution | Rejected as primary (still used as V6a fallback) |
| **Server-Sent Events (SSE)** | Server streams events over HTTP; browser uses native `EventSource` | One-way server→client is exactly our need; auto-reconnect; native browser API; works through proxies | Server→client only (but we don't need client→server streaming in V6) | **Adopted for V6b** |
| WebSocket | Bidirectional full-duplex | Powerful; bidirectional | Overkill for one-way event streaming; more complex handshake and lifecycle | Deferred to V7 (needed for multi-user shared rooms) |

**Frontend framework:**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| **Vanilla ES modules** | Plain HTML/CSS/JS with native `import`/`export`, no build step | Zero build tooling; zero npm; direct debugging; small surface area for a single-page visualization | Requires discipline to avoid spaghetti; no virtual DOM ergonomics if it grows large | **Adopted for V6** — sufficient for single-page app; structured layering leaves a clean migration path |
| React / Vue | Component framework | Rich ecosystem; great for large apps | Build toolchain overhead; npm dependency; overkill for current scope | Deferred — possible migration if frontend grows |
| Svelte | Compile-to-vanilla framework | Compiled output is lean | Still requires build step; extra learning | Rejected |

**JSON mapping:**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| **Jackson** | Industry-standard Java JSON library | First-class Javalin integration; record support; streaming API if needed later | Extra transitive deps (jackson-core, jackson-databind) | **Adopted** — standard choice with Javalin |
| Gson | Google's JSON library | Simpler | Less idiomatic with Javalin; would need custom `JsonMapper` wiring | Rejected |
| Hand-rolled | Manual JSON building | No dependency | Tedious; error-prone; no schema validation | Rejected |

**Session & state model:**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| Stateless (config sent per run) | Browser sends full config on each run | No server state | Can't stream events incrementally; no pause/resume; makes V6c/d hard | Rejected |
| **Server-side `Session` with ID** | Each browser has a unique session ID (cookie); server holds `Session` → `Arena` mapping | Enables SSE streaming, pause/resume, future multi-user; clean separation | Requires session lifecycle management (TTL cleanup) | **Adopted** |
| Persistent DB-backed sessions | Sessions in SQL/Redis | Survive server restart | Over-engineered for current scope | Deferred |

#### Design Discussion

- **Deployment model:** Single self-contained JAR. `java -jar rover.jar --web` starts Javalin on port 8080 (configurable via `--port`), serves static assets from the JAR's classpath, and opens REST + SSE endpoints. No install, no Node, no build step for the user.
- **Domain reuse:** V0–V5 core classes (`Arena`, `Rover`, `Environment`, `ActionParser`, `RoverListener`, `RoverEvent`) are used **unchanged**. V6 adds only a thin web layer on top. The `RoverListener` pattern is exactly what we need — we register an `SseRoverListener` that forwards events to connected browser clients.
- **Backward compatibility:** CLI modes (single-rover, arena, visual, verbose) remain fully functional. The `--web` flag is a new mode, orthogonal to the others. All existing V0–V5 tests stay green.
- **Frontend layering (critical for future extensibility):** Even with vanilla JS, the code is strictly layered to enable a future React/Vue migration without rewriting business logic:
  ```
  public/
  ├── index.html                 — single page shell
  ├── css/
  │   ├── base.css               — layout, typography
  │   └── themes.css             — CSS variables per theme (Modern/Minimal/Mono) mirroring V5c
  └── js/
      ├── main.js                — app entry; wires modules together
      ├── api/
      │   ├── client.js          — REST + SSE client (pure data layer)
      │   └── types.js           — JSDoc DTO definitions
      ├── state/
      │   └── store.js           — observable store (subscribe/publish pattern)
      ├── ui/
      │   ├── controls.js        — form inputs (grid size, rover list)
      │   ├── canvas.js          — arena renderer (draw grid, rovers, trails, obstacles)
      │   ├── toolbar.js         — run/pause/step/theme controls
      │   └── editor.js          — click-to-place obstacle / add rover (V6c)
      └── util/
          └── events.js          — simple event emitter
  ```
  Migration path to React/Vue: replace the `ui/` module one file at a time; `api/` and `state/` stay intact. Canvas rendering is framework-agnostic.
- **Canvas over SVG:** Canvas has better performance for animated grids and a simpler programming model for redraw-on-event. SVG would be nice for accessibility but overkill.
- **Theme parity with V5c:** The three V5c themes (`modern`, `minimal`, `mono`) are mirrored as CSS theme classes so the web UI matches the terminal look. Theme switch is instant (CSS variable change, no reload).
- **Session lifecycle:** Each browser hits `POST /api/session` on first load and receives a UUID. The UUID is stored in `sessionStorage` (tab-scoped) so opening a new tab creates a fresh arena. Sessions have a 30-minute idle TTL; a `ScheduledExecutorService` reaps expired sessions.
- **Concurrency model:** `SessionManager` uses a `ConcurrentHashMap`. Each `Session` serializes its own execution on a single-threaded executor — one run at a time per session. Multiple sessions run truly concurrently. This matches the existing thread-safety guarantees of `Rover` and `Arena`.
- **SSE scope:** Each session has a `CopyOnWriteArrayList<SseClient>` of connected SSE streams. Events are fan-out: one execution → N subscribers (typically 1, but ready for multi-user in V6d/V7). Events are serialized to JSON on the emitting thread so listeners never block the rover.
- **Graceful shutdown:** JVM shutdown hook closes Javalin cleanly, flushes SSE streams with a `complete` event, and releases the port.
- **Security (MVP scope):** V6 is localhost-only. No auth, no CORS, no HTTPS. A `--bind` flag can later expose it to LAN. Full auth/TLS is a V7 concern when we deploy to a real server.

#### Canvas Layout, Legend, and Viewport

The V6a page is a single screen divided into three logical regions:

```
┌────────────────────────────────────────────────────────────────┐
│  Header: App title + theme selector                           │
├────────────────────────────┬───────────────────────────────────┤
│  Canvas (600×600 px)       │  Config panel                     │
│    - axis labels           │    - Grid size inputs             │
│    - grid cells            │    - Conflict policy              │
│    - rovers + trails       │    - Obstacle list                │
│    - obstacles             │    - Rover list (add/delete)      │
│                            │    - Parallel toggle              │
│  Legend (below canvas)     │    - Run / Reset buttons          │
│    - Symbols row           │                                   │
│    - Colors row (dynamic)  │                                   │
│    - Grid info row         │                                   │
├────────────────────────────┴───────────────────────────────────┤
│  Status bar (bottom): state + last run + stats                │
└────────────────────────────────────────────────────────────────┘
```

**Legend block (below the canvas):** A fixed three-row panel that reflects the current state:

| Row | Content | Source |
|-----|---------|--------|
| **Symbols** | `▲▼◄►` (rover facing), `█` (obstacle), `·` (trail) — static text with theme-colored glyphs | Hard-coded, rendered once |
| **Colors** | One dot+label per active rover (e.g., `● R1  ● R2  ● R3`) + obstacle color + trail color | Generated dynamically from the rover list in the store; updates when rovers are added/removed |
| **Grid info** | Mode (Bounded/Unbounded), dimensions (`10 × 10` or `∞`), viewport (`x[0..9] y[0..9]`), origin note (`Origin (0,0) = bottom-left`) | Derived from current config + last snapshot |

**Viewport strip (above the canvas):** A thin single-line label showing `Viewport: (xMin, yMin) – (xMax, yMax)`. For bounded mode this matches the grid; for unbounded mode it reflects the auto-fit range. This makes it obvious *which part of the world* the canvas is currently showing.

**Status bar (bottom of page):** Three lines:
1. `Status: ready | running | done | error: <message>`
2. `Last run: R1 → (x,y) DIR   R2 → (x,y) DIR   ...`
3. `Stats: Steps: N    Blocked: N    Duration: Nms    Rovers: N    Obstacles: N`

#### Grid Sizing & Auto-Fit Viewport (Unbounded Mode)

Grid dimensions are **optional** on the frontend. The backend accepts three states:

| `width` | `height` | Resulting environment | Viewport behavior |
|---------|----------|-----------------------|-------------------|
| `null` | `null` | `UnboundedEnvironment` | Auto-fit based on rovers + trails + obstacles |
| `≥ 1` | `≥ 1` | `GridEnvironment(w, h)` | Fixed at `x[0..w-1] y[0..h-1]` |
| `null` | `≥ 1` | **400 Bad Request** | `INVALID_GRID`: both dimensions required (or both empty) |
| `≥ 1` | `null` | **400 Bad Request** | Same |
| `≤ 0` | *any* | **400 Bad Request** | `INVALID_GRID`: dimensions must be positive |

**Auto-fit viewport calculation (unbounded mode only):**

```
rovers         = all rover start positions + current positions + trail positions
obstacles      = all obstacle positions
points         = rovers ∪ obstacles ∪ {(0, 0)}        // origin always included
bbox           = min/max of points.x and points.y
padding        = 2 cells on each side
minSize        = 10 × 10 (viewport never smaller than this, centered on bbox)
viewport       = expand(bbox, padding).clampMin(minSize)
```

**When recomputed:**
- On session creation (before any config) → default viewport `x[-5..4] y[-5..4]`
- On config submit → recomputed from rover start positions + obstacles
- On run completion → recomputed from full trails + final positions

**V6a uses static auto-fit**: viewport is recomputed *once* per request (after configure or after run). The canvas redraws fully. V6b will add "smooth expansion" during live animation.

**Canvas cell sizing:**
- Canvas has fixed pixel dimensions (600 × 600 px by default).
- `cellSize = floor(600 / max(viewportWidth, viewportHeight))`.
- Minimum cell size: 12 px (below this, symbols become unreadable).
- If the computed cell size falls below 12, the canvas shows an overflow hint and renders only the top-left region that fits — user should shrink the grid or narrow the rover trail range. (Edge case, unlikely in normal usage.)

**Server-side viewport computation:** The viewport is computed on the server inside `ViewportCalculator` and included in every `SessionSnapshot`. This centralizes the logic (single source of truth), avoids duplicating it in JS, and keeps the frontend purely presentational.

#### Bug Fix: Frontend Input Cursor Loss

**Root cause:** `renderControls()` in `controls.js` tears down and rebuilds the entire rover card DOM (`el.roverList.innerHTML = ""` + `appendChild(buildRoverCard(...))`) on *every* store update. Since every keystroke in the commands input fires an `input` event → store update → re-render, the `<input>` element the user is typing in is destroyed and replaced with a fresh one, causing focus and cursor position to be lost.

Same pattern exists for the obstacle list (`el.obstacleList.innerHTML = ""`), though it's less noticeable because obstacles have no text inputs.

**Fix approach — structural vs value re-renders:**

Split `renderControls` into two paths:

| Trigger | DOM action | Example |
|---------|-----------|---------|
| **Structural change** — rover count or IDs changed, obstacle count changed | Tear down and rebuild affected section | User clicks "+ Add rover" or "×" delete |
| **Value change** — commands, position, direction of an existing rover changed | Update existing DOM element values in-place; **skip elements that have focus** (the user is actively typing in them) | User types "M" in the commands box |

Implementation:
1. Track `lastRoverIds` (array of rover ID strings) across renders.
2. On each render, compare current IDs to `lastRoverIds`.
3. If IDs match → call `updateRoverCardValues()` which iterates existing card DOM and patches values, skipping `document.activeElement`.
4. If IDs differ → full rebuild (same as current behavior), then update `lastRoverIds`.
5. Same pattern for obstacles: track `lastObstacleCount`, only rebuild if count changed.

This eliminates cursor loss while keeping the UI reactive to all state changes.

#### Feature: Continue Run (Incremental Execution)

**Problem:** Currently `Session.run()` re-parses and re-executes commands on a freshly built Arena. After a run completes, the user cannot send new commands that continue from the rovers' current positions — they must Reset and start over.

**Desired behavior:**

| Action | What happens |
|--------|-------------|
| **Configure** | Builds the Arena with rovers at their configured starting positions. Resets trails, stats. |
| **Run** (first time) | Parses the commands currently in each rover's input box. Executes them on the Arena. Rovers stop at their final positions. Trails and stats are accumulated. |
| **Run** (subsequent) | User edits command boxes (new commands). Clicks Run again. New commands are parsed and executed **from the rovers' current positions** — the Arena is NOT rebuilt. Trails and stats are **appended** (not replaced). |
| **Reset** | Rebuilds the Arena from the original config — rovers return to their starting positions, trails and stats are cleared. |

**Strategy comparison:**

| Approach | Description | Pros | Cons | Verdict |
|----------|-------------|------|------|---------|
| Rebuild Arena on every run | Current behavior | Simple | Cannot continue from current position | Current — being replaced |
| **Reuse Arena, only parse new commands** | Arena persists between runs; `run()` only feeds new commands to existing rovers | Natural "continue" semantics; trails accumulate; clean separation of configure vs run | Need to handle rover state carefully; "Reset to start" must be explicit | **Adopted** |
| Two buttons: "Run" + "Run from start" | User chooses which mode | Maximum flexibility | Cluttered UI; confusing for simple use case | Rejected |

**Backend changes:**

| Class | Change |
|-------|--------|
| `Session.run()` | No longer rebuilds Arena. Reads current commands from the `ArenaConfig.rovers`, parses them, executes on the **existing** Arena. Listeners accumulate trails (append, not replace). Stats from each run are added to the running totals. |
| `Session.configure()` | Unchanged — always rebuilds the Arena from scratch. Resets trails and stats. This is the "hard reset". |
| `Session.resetToStart()` | **New method.** Rebuilds the Arena from the stored config (same as `configure(config)` but without requiring the frontend to re-send the config). Called by "Reset" button. |
| `RoverController` | New endpoint: `POST /api/session/{id}/reset` — calls `session.resetToStart()` and returns the fresh snapshot. |

**Frontend changes:**

| Component | Change |
|-----------|--------|
| `toolbar.js` — Run button | After a successful run, commands inputs are NOT cleared. User can edit them and click Run again to continue. |
| `toolbar.js` — Reset button | Calls `POST /api/session/{id}/reset` instead of `DELETE` + `POST /api/session` + `PUT /config`. |
| `canvas.js` | Trail rendering already accumulates (no change needed on canvas side). |
| `statusBar.js` | Stats should show **cumulative** totals (all runs combined). Add a "Runs: N" counter. |

**REST endpoint additions:**

| Method | Path | Behavior |
|--------|------|----------|
| `POST` | `/api/session/{id}/reset` | Rebuilds Arena from stored config. Returns 200 + fresh snapshot. 404 if session missing. 409 if currently running. |

**Example user flow:**

```
1. Configure: 10×10 grid, R1 at (0,0) facing N
2. Commands: "MMR" → Run → R1 ends at (0,2) facing E. Trails: [(0,0),(0,1),(0,2)]
3. Commands: "MM"  → Run → R1 continues from (0,2)E → ends at (2,2) facing E. Trails: [...,(1,2),(2,2)]
4. Commands: "LMM" → Run → R1 continues from (2,2)E → turns N → ends at (2,4). Trails: [...,(2,3),(2,4)]
5. Reset → R1 back at (0,0) facing N. Trails cleared.
```

#### Class & Data Structure Changes

##### V6a — Web Server + REST API + Static Frontend

###### `WebApp` — Class (New)
Entry point for web mode. Starts Javalin, registers routes, serves static assets.

| Member | Type | Description |
|--------|------|-------------|
| `port` | `int` | TCP port (default 8080) |
| `sessionManager` | `SessionManager` | Shared session store |
| `javalin` | `Javalin` | The embedded server instance |

| Method | Signature | Description |
|--------|-----------|-------------|
| `start` | `void start()` | Starts the server on the configured port |
| `stop` | `void stop()` | Stops the server cleanly |
| `main` | `static void main(String[] args)` | CLI entry: parses `--web [--port N]`, starts the server |

###### `SessionManager` — Class (New)
Thread-safe registry of browser sessions.

| Member | Type | Description |
|--------|------|-------------|
| `sessions` | `ConcurrentHashMap<String, Session>` | Session ID → `Session` |
| `reaper` | `ScheduledExecutorService` | Background thread that evicts idle sessions |
| `ttlMinutes` | `long` | Idle TTL (default 30) |

| Method | Signature | Description |
|--------|-----------|-------------|
| `createSession` | `Session createSession()` | Creates a new session with a UUID |
| `getSession` | `Session getSession(String id)` | Retrieves an existing session; returns null if missing |
| `removeSession` | `void removeSession(String id)` | Explicit cleanup |
| `reapExpired` | `void reapExpired()` | Evicts sessions idle beyond TTL |
| `shutdown` | `void shutdown()` | Stops the reaper and clears state |

###### `Session` — Class (New)
Per-browser state: arena, event queue, SSE subscribers, last-access timestamp.

| Member | Type | Description |
|--------|------|-------------|
| `id` | `String` | UUID session identifier |
| `arena` | `Arena` | The session's Arena instance (created after config) |
| `config` | `ArenaConfig` | Current grid/obstacle/rover configuration |
| `trails` | `Map<String, List<Position>>` | Path trails per rover, populated by an internal `RoverListener` |
| `stats` | `RunStats` | Most recent execution stats |
| `lastAccess` | `volatile long` | Timestamp of most recent request (for TTL) |
| `executor` | `ExecutorService` | Single-threaded per-session executor |
| `subscribers` | `CopyOnWriteArrayList<SseClient>` | Connected SSE streams (used from V6b onward) |

| Method | Signature | Description |
|--------|-----------|-------------|
| `configure` | `void configure(ArenaConfig config)` | Validates and builds a new `Arena` from config; resets trails and stats |
| `run` | `Future<?> run()` | Executes configured rover commands asynchronously; populates trails and stats |
| `getSnapshot` | `SessionSnapshot getSnapshot()` | Builds a `SessionSnapshot` with current rovers, trails, auto-fit viewport, and stats |
| `subscribe` | `void subscribe(SseClient client)` | Registers an SSE subscriber (stub in V6a; functional in V6b) |
| `unsubscribe` | `void unsubscribe(SseClient client)` | Removes an SSE subscriber |
| `touch` | `void touch()` | Updates `lastAccess` to now |

###### `ArenaConfig` — Record (New)
Immutable configuration submitted by the browser. `width`/`height` are nullable to support unbounded mode.

| Field | Type | Description |
|-------|------|-------------|
| `width` | `Integer` | Grid width, or `null` for unbounded |
| `height` | `Integer` | Grid height, or `null` for unbounded |
| `wrap` | `boolean` | Toroidal boundary mode (ignored when width/height are null) |
| `obstacles` | `List<PositionDto>` | Obstacle coordinates |
| `conflictPolicy` | `String` | `"fail"` / `"skip"` / `"reverse"` |
| `rovers` | `List<RoverSpecDto>` | Rover definitions |
| `parallel` | `boolean` | Parallel execution mode |

**Validation rules:**
- `width` and `height` must either both be null (unbounded) or both be positive integers.
- `conflictPolicy` must be one of the three string values (case-insensitive).
- `rovers` must contain at least one rover.
- Each `RoverSpecDto.direction` must be `"N"` / `"E"` / `"S"` / `"W"` (case-insensitive).
- Each `RoverSpecDto.commands` must only contain registered command characters.
- Rover IDs must be unique.

###### `RoverSpecDto` — Record (New)

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Rover identifier (e.g., "R1") |
| `x` | `int` | Starting x coordinate |
| `y` | `int` | Starting y coordinate |
| `direction` | `String` | `"N"` / `"E"` / `"S"` / `"W"` |
| `commands` | `String` | Command string (e.g., "MMRML") |

###### `PositionDto` — Record (New)
`(int x, int y)`. JSON-serializable equivalent of the domain `Position`.

###### `SessionSnapshot` — Record (New)
State summary returned by `GET /api/session/{id}/state`. Everything the frontend needs to render the canvas in one payload.

| Field | Type | Description |
|-------|------|-------------|
| `sessionId` | `String` | Session ID |
| `config` | `ArenaConfig` | Current configuration (for UI sync) |
| `rovers` | `Map<String, RoverStateDto>` | Current rover states keyed by ID |
| `trails` | `Map<String, List<PositionDto>>` | Path trails per rover, in order of visitation |
| `viewport` | `ViewportDto` | Current viewport range (bounded = grid; unbounded = auto-fit) |
| `stats` | `RunStats` | Summary of the most recent run (or zeros if no run yet) |
| `running` | `boolean` | Whether a run is in progress |

###### `RoverStateDto` — Record (New)

| Field | Type | Description |
|-------|------|-------------|
| `x` | `int` | Current x |
| `y` | `int` | Current y |
| `direction` | `String` | Facing direction (`"NORTH"` / `"EAST"` / `"SOUTH"` / `"WEST"`) |

###### `ViewportDto` — Record (New)
Describes the visible coordinate range. For bounded grids this matches the grid dimensions; for unbounded grids this is computed by `ViewportCalculator`.

| Field | Type | Description |
|-------|------|-------------|
| `xMin` | `int` | Inclusive minimum x |
| `yMin` | `int` | Inclusive minimum y |
| `xMax` | `int` | Inclusive maximum x |
| `yMax` | `int` | Inclusive maximum y |

Convenience getters: `width()` returns `xMax - xMin + 1`, `height()` returns `yMax - yMin + 1`.

###### `RunStats` — Record (New)
Summary metrics for the most recent execution, shown in the status bar.

| Field | Type | Description |
|-------|------|-------------|
| `totalSteps` | `int` | Total actions executed across all rovers |
| `blockedCount` | `int` | Number of blocked moves across all rovers |
| `durationMs` | `long` | Wall-clock execution time in milliseconds |
| `roverCount` | `int` | Number of rovers in the session |
| `obstacleCount` | `int` | Number of obstacles in the session |

###### `ViewportCalculator` — Class (New)
Pure static helper that computes viewports. Centralizes the auto-fit logic so server and potential future clients share one implementation.

| Method | Signature | Description |
|--------|-----------|-------------|
| `forBoundedGrid` | `static ViewportDto forBoundedGrid(int width, int height)` | Returns `(0, 0, width-1, height-1)` |
| `autoFit` | `static ViewportDto autoFit(Collection<Position> points)` | Bounding box + 2-cell padding, enforcing a 10×10 minimum centered on the bbox; includes origin `(0,0)` as a sentinel |

###### `ArenaConfigMapper` — Class (New)
Pure static helper that validates an `ArenaConfig` DTO and builds the corresponding domain objects.

| Method | Signature | Description |
|--------|-----------|-------------|
| `buildEnvironment` | `static Environment buildEnvironment(ArenaConfig config)` | Returns `UnboundedEnvironment` (both dims null) or `GridEnvironment` (both dims set); throws `ConfigValidationException` on partial/invalid dims |
| `buildConflictPolicy` | `static ConflictPolicy buildConflictPolicy(String name)` | Case-insensitive `fail`/`skip`/`reverse`; throws on unknown |
| `buildArena` | `static Arena buildArena(ArenaConfig config)` | Creates `Arena`, registers each rover, parses commands; throws on any validation failure |
| `parseDirection` | `static Direction parseDirection(String s)` | `N`/`E`/`S`/`W` → enum; throws on unknown |

###### `ConfigValidationException` — Class extends `IllegalArgumentException` (New)
Thrown by `ArenaConfigMapper` when validation fails. Carries a short machine-readable `code` (e.g., `INVALID_GRID`, `UNKNOWN_DIRECTION`, `DUPLICATE_ROVER_ID`) plus a human-readable message.

###### `RoverController` — Class (New)
Javalin route handlers. Stateless; receives `SessionManager` via constructor.

| Method | Signature | Description |
|--------|-----------|-------------|
| `createSession` | `void createSession(Context ctx)` | `POST /api/session` → returns `{sessionId}` |
| `configure` | `void configure(Context ctx)` | `PUT /api/session/{id}/config` → validates and stores config |
| `run` | `void run(Context ctx)` | `POST /api/session/{id}/run` → triggers async execution |
| `getState` | `void getState(Context ctx)` | `GET /api/session/{id}/state` → returns `SessionSnapshot` |
| `deleteSession` | `void deleteSession(Context ctx)` | `DELETE /api/session/{id}` |

###### `WebError` — Record (New)
Uniform error response: `{code, message}`. Returned on validation errors, missing sessions, etc.

###### `App` — Modified
Add `--web` and `--port` flags. When `--web` is present, delegate to `WebApp.main()`; otherwise existing CLI behavior.

##### V6b — Real-Time Event Streaming (SSE)

###### `SseRoverListener` — Class implements `RoverListener` (New)
Bridges domain events to SSE subscribers. One instance per session (or per rover inside a session).

| Member | Type | Description |
|--------|------|-------------|
| `session` | `Session` | The owning session (for subscriber fan-out) |
| `roverId` | `String` | ID of the rover this listener watches |
| `jsonMapper` | `JsonMapper` | Jackson mapper for event serialization |

| Method | Signature | Description |
|--------|-----------|-------------|
| `onStep` | `void onStep(RoverEvent event)` | Serialize to JSON, push to all session subscribers as SSE event `step` |
| `onComplete` | `void onComplete(RoverState finalState)` | Push SSE event `complete` |

###### `RoverEventDto` — Record (New)
JSON-friendly projection of `RoverEvent`.

| Field | Type | Description |
|-------|------|-------------|
| `roverId` | `String` | Which rover |
| `from` | `RoverStateDto` | Previous state |
| `to` | `RoverStateDto` | New state |
| `action` | `String` | Action name (e.g., "MoveForward") |
| `stepIndex` | `int` | Step index |
| `totalSteps` | `int` | Total steps |
| `blocked` | `boolean` | Blocked flag |

###### `RoverController` — Modified
Add SSE endpoint.

| Method | Signature | Description |
|--------|-----------|-------------|
| `subscribeEvents` | `void subscribeEvents(Context ctx)` | `GET /api/session/{id}/events` → opens SSE stream, subscribes to session |

##### V6c — Interactive Editing (Frontend)

No new Java classes. Frontend-only changes:
- `ui/editor.js` — click handlers for placing obstacles, adding rovers, setting direction
- `ui/toolbar.js` — run/pause/step/reset/theme buttons
- `ui/canvas.js` — drag-select for multi-obstacle placement, highlight hovered cell
- `state/store.js` — undo/redo for edits (V6c polish)

Backend gains one new REST action: `POST /api/session/{id}/pause` and `POST /api/session/{id}/resume`. This requires a small addition to `Session.run()` for cooperative pause/resume (a `volatile boolean paused` flag checked in the step loop).

##### V6d — Session Isolation (Multi-User Groundwork)

Mostly a hardening phase, not new classes:
- `SessionManager.reapExpired()` exercised by tests with injectable `Clock`
- Concurrent session stress tests (many sessions running in parallel)
- Per-session isolation tests (session A cannot affect session B)
- Optional `--bind` flag on `WebApp` to expose beyond localhost (default stays `127.0.0.1`)

#### Test Plan

| Dimension | Covers | Key Scenarios |
|-----------|--------|---------------|
| **V6a — Web server startup** | `WebApp` lifecycle | Start on random port, verify listening, stop cleanly; double-start rejected; port conflict handled |
| **V6a — Session lifecycle** | `SessionManager`, `Session` | Create session returns UUID, get by ID, remove, ID not found returns null, TTL eviction via injected clock |
| **V6a — REST: createSession** | `POST /api/session` | Returns 200 with JSON `{sessionId}`; multiple calls return distinct IDs |
| **V6a — REST: configure** | `PUT /api/session/{id}/config` | Valid config accepted; missing fields rejected with 400; invalid direction rejected; grid size validation; unknown session returns 404 |
| **V6a — REST: run** | `POST /api/session/{id}/run` | Triggers execution; returns 202 accepted; run without config returns 409; concurrent run on same session returns 409 |
| **V6a — REST: getState** | `GET /api/session/{id}/state` | Returns current rover positions; reflects config; running flag correct |
| **V6a — REST: deleteSession** | `DELETE /api/session/{id}` | Removes session; subsequent GET returns 404 |
| **V6a — JSON mapping** | DTO serialization | `ArenaConfig`, `RoverSpecDto`, `SessionSnapshot` round-trip via Jackson; unknown fields ignored; missing required fields rejected |
| **V6a — Config → Arena** | `ArenaConfigMapper` | Bounded grid, unbounded grid (null dims), all conflict policies, direction parsing, rover ID uniqueness, duplicate ID rejected, invalid direction rejected, partial dims (`INVALID_GRID`) rejected, empty rover list rejected, invalid commands rejected |
| **V6a — Viewport auto-fit** | `ViewportCalculator` | Single point → 10×10 min window centered on it; multiple points → bounding box + 2 padding; includes `(0,0)` origin; empty input → default 10×10 centered on origin; bounded grid → `(0,0)–(w-1,h-1)` exactly |
| **V6a — Session snapshot** | `Session.getSnapshot()` | Contains config, rovers, trails, viewport, stats, running flag; trails match execution order; stats reflect last run; viewport auto-fit in unbounded mode; bounded mode viewport equals grid |
| **V6a — Session trail tracking** | `Session` internal listener | Each rover's trail populated during run in order of positions visited; reset on re-configure |
| **V6a — End-to-end** | Full REST flow | Create → configure → run → state → delete; verify final positions, trails, viewport, and stats match expected |
| **V6a — Continue Run** | Incremental execution | Run → change commands → Run again → verify second run starts from first run's endpoint; trails accumulate; stats accumulate; Reset returns rovers to start |
| **V6a — Reset endpoint** | `POST /reset` | Reset restores starting positions and clears trails/stats; Reset on unconfigured session returns 409; Reset while running returns 409 |
| **V6a — Input cursor stability** | Frontend controls | Rapid typing in commands box does not lose cursor; add/remove rover rebuilds cards; changing rover values in-place does not rebuild DOM |
| **V6a — Static assets** | Javalin static serving | `GET /` returns `index.html`; CSS/JS served with correct MIME type; 404 for missing files |
| **V6a — Error responses** | `WebError` | Consistent error envelope; validation errors carry field name; unknown session returns uniform error |
| **V6a — `--web` CLI flag** | `App` integration | `--web` starts server; `--web --port 9090` uses custom port; without `--web` CLI behavior unchanged |
| **V6b — SSE subscription** | `GET /events` | Open stream, receive `step` events in order, receive final `complete` event, stream closes cleanly |
| **V6b — SseRoverListener** | Event forwarding | `onStep` emits JSON to all subscribers; `onComplete` emits final event; multiple subscribers all receive events |
| **V6b — Event JSON format** | `RoverEventDto` | Field names match frontend expectations; `blocked` flag preserved; action name stripped of "Action" suffix |
| **V6b — Fast execution** | Timing edge case | Very fast (delay=0) runs deliver all events in order without loss |
| **V6b — Subscription lifecycle** | Connect/disconnect | Subscriber added/removed correctly; closed stream doesn't throw when events fire; graceful server shutdown flushes streams |
| **V6c — Pause/resume** | `Session.run()` cooperative pause | Pause between steps, resume continues, reset clears state; pause on completed run is no-op |
| **V6c — Frontend smoke tests** | Manual checklist | Grid rendered at correct size, click places obstacle, add rover button creates new form row, run button animates rovers, theme switch changes CSS variables |
| **V6d — Session isolation** | Cross-session safety | Two sessions run concurrently with different configs; events from session A only go to session A subscribers; deleting session A does not affect session B |
| **V6d — TTL cleanup** | `SessionManager.reapExpired` | Sessions idle beyond TTL are removed; `touch()` extends lifetime; reaper thread runs on schedule |
| **V6d — Concurrent sessions stress** | Many parallel sessions | 20 sessions running simultaneously without state leakage; thread-safety of `SessionManager` verified |
| **Backward compatibility** | All existing modes | V0–V5 CLI modes unchanged; all existing tests pass unmodified; new tests additive |
| **Coverage** | Branch coverage | 95%+ branch coverage maintained with Jacoco verification |

---

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

### V2 — Geographic Constraints — Completed

**Scope:** Allow users to optionally specify a finite grid (`width × height`), obstacle positions, boundary mode (bounded or wrap), and conflict resolution policy (fail, skip, or reverse). Rover validates moves against environment constraints before committing state. All constraints are optional — omitting them preserves V1 behavior (infinite plane, no obstacles). CLI extended with `--grid`, `--wrap`, `--obstacles`, and `--on-conflict` flags.

- [x] `ConflictPolicy` enum (`FAIL`, `SKIP`, `REVERSE`)
- [x] `BoundaryMode` enum (`BOUNDED`, `WRAP`)
- [x] `MoveResult` record (`Position position`, `boolean blocked`)
- [x] `Environment` interface with `MoveResult validate(Position current, Position proposed)` method
- [x] `UnboundedEnvironment` — no constraints, default for backward compatibility
- [x] `GridEnvironment` — finite grid with width/height, `Set<Position>` obstacles, configurable `BoundaryMode`
- [x] `MoveBlockedException` — thrown when `ConflictPolicy.FAIL` is active and move is blocked
- [x] Modify `Rover` — add optional `Environment` + `ConflictPolicy` fields; apply policy in `execute()` when move is blocked
- [x] Modify `App` — parse `--grid`, `--wrap`, `--obstacles`, `--on-conflict` CLI flags
- [x] Test suite: boundary BOUNDED/WRAP, obstacles, three conflict policies (FAIL/SKIP/REVERSE), backward compatibility, mixed sequences, error handling, CLI integration, concurrency
- [x] Coverage verification (maintain 95%+ branch coverage)

### V3 — Additional Commands — Completed

**Scope:** Add five new commands: B (backward), S (speed boost/jump), U (u-turn), Z (undo), Y (redo). New movement actions leverage the existing Strategy pattern and ActionParser registry. Undo/redo uses state history stacks in Rover with marker Action classes detected via instanceof. Add `--verbose` flag for text-only step reporting. All changes are additive — existing commands and behavior unchanged.

- [x] `BackwardAction` (B): move one step opposite to facing, direction unchanged
- [x] `SpeedBoostAction` (S): jump two cells forward (final position validated only)
- [x] `UTurnAction` (U): reverse direction 180°
- [x] `UndoAction` (Z) + `RedoAction` (Y): marker actions for undo/redo
- [x] Modify `Rover`: add history/redo stacks, detect undo/redo in `executeOne()`
- [x] Modify `ActionParser`: register B, S, U, Z, Y by default
- [x] `VerboseListener` + Modify `App`: add `--verbose` flag for text-only step reporting
- [x] Test suite: each new action, undo/redo (basic + edge cases), mixed commands, environment integration, listener integration, verbose mode
- [x] Coverage verification (maintain 95%+ branch coverage)

### V4 — Multi-Rover Control — Completed

**Scope:** Manage multiple rovers on a shared grid via an `Arena` class. Each rover has a unique ID for selection and command routing. Collision avoidance is handled transparently through `ArenaEnvironment` (wraps base Environment + dynamic rover positions). Two execution modes: sequential (one rover at a time) and parallel (round-robin). CLI extended with `--arena`, `--rover "ID:x,y,dir:commands"`, and `--parallel` flags. Without `--arena`, single-rover mode unchanged.

- [x] `ArenaEnvironment` — wraps base Environment, adds rover collision detection via `arena.isOccupied()`
- [x] `Arena` — fleet manager: rover registry (`createRover`, `removeRover`, `getRover`), position tracking, `isOccupied()`
- [x] `Arena.executeSequential()` — one rover completes all commands, then the next
- [x] `Arena.executeParallel()` — round-robin: one step per rover per round, mutual collision = both blocked
- [x] Modify `App` — add `--arena`, `--rover`, `--parallel` CLI flags
- [x] `ArenaRenderer` — multi-rover visual rendering with distinct labels (A/a, B/b) on shared grid
- [x] Test suite: rover lifecycle, collision avoidance (all 3 ConflictPolicies), sequential/parallel execution, combined constraints (arena + grid + obstacles), visual mode, CLI parsing, backward compatibility
- [x] Coverage verification (maintain 95%+ branch coverage)

### V5a — Observer + Step Execution — Completed

**Scope:** Add Observer pattern to Rover so external components can observe each step of execution. Introduce `RoverListener` interface, `RoverEvent` record, and `StepExecutor` for paced action execution with configurable delay. This is the pure backend foundation — no UI dependency. All existing behavior remains backward compatible (no listeners = no overhead).

- [x] `RoverEvent` record (previousState, newState, action, stepIndex, totalSteps, blocked)
- [x] `RoverListener` interface (`onStep`, `onComplete`)
- [x] Modify `Rover`: add `listeners` list, `addListener()`, `removeListener()`, notify in `executeOne()`
- [x] `StepExecutor`: synchronous `execute()` with configurable delay, async `executeAsync()` on background thread
- [x] Test suite: observer notification, listener lifecycle, StepExecutor timing, conflict policy integration
- [x] Coverage verification (maintain 95%+ branch coverage)

### V5b — Terminal Renderer — Completed

**Scope:** Implement `TerminalRenderer` as a `RoverListener` that draws the grid, rover, path trail, and obstacles using ANSI escape codes. Add `--visual` and `--delay <ms>` CLI flags to App. In visual mode, use `StepExecutor` + `TerminalRenderer` for animated step-by-step output. Without `--visual`, behavior is unchanged.

- [x] `TerminalRenderer` implements `RoverListener`: ANSI-based grid rendering with directional arrows, path trail, obstacles
- [x] Modify `App`: add `--visual` and `--delay <ms>` flags; wire StepExecutor + TerminalRenderer in visual mode
- [x] Grid size inference for unbounded environments (default viewport centered on origin)
- [x] Test suite: renderer output, CLI flag parsing, backward compatibility
- [x] Coverage verification (maintain 95%+ branch coverage)

### V5c — Enhanced Terminal UI

**Scope:** Upgrade `TerminalRenderer` from a bare-bones monochrome display to a polished, modern terminal experience. Introduce a `Theme` abstraction with three implementations (`ModernTheme`, `MinimalTheme`, `MonoTheme`) that control all visual aspects — colors, symbols, borders, and animation behavior. Add `AnsiStyle` utility for centralized ANSI escape code generation, `GridFrame` for Unicode box-drawing borders with axis labels, and `StatusBar` for a real-time dashboard (progress bar, command preview, rover state). Rendering is upgraded to flicker-free cursor-home overwrite, and path trails gain a color gradient based on recency. Blocked moves flash in a warning color. Multi-rover mode gets per-rover color coding. CLI extended with `--theme` flag. Graceful degradation to `MonoTheme` on unsupported terminals.

- [x] `AnsiStyle` utility class: `fg256`, `bg256`, `bold`, `dim`, `reset`, `cursorHome`, `hideCursor`, `showCursor`
- [x] `Theme` interface: `roverColor`, `roverSymbol`, `pathColor`, `pathSymbol`, `obstacleColor`, `obstacleSymbol`, `emptySymbol`, `borderColor`, `blockedColor`, `statusStyle`, `gradientWindow`
- [x] `ModernTheme`: cyan rover, blue→gray gradient trail, white box-drawing frame, red blocked flash, green progress bar
- [x] `MinimalTheme`: white rover, gray gradient trail, dim frame, yellow blocked flash
- [x] `MonoTheme`: no color, plain ASCII symbols, V5b-compatible fallback
- [x] `GridFrame`: box-drawing borders (`─ │ ┌ ┐ └ ┘`), axis labels (column/row numbers)
- [x] `StatusBar`: progress bar (`████░░░░░░`), command preview with current action highlighted, rover state display, blocked-move warning
- [x] Refactor `TerminalRenderer`: compose with `Theme` + `GridFrame` + `StatusBar`; cursor-home overwrite (no screen clear); gradient path trail; blocked-move flash
- [x] Multi-rover color coding: per-rover color from theme palette, colored path trails per rover
- [x] Modify `App`: add `--theme modern/minimal/mono` flag; auto-detect terminal capability via `TERM` env var
- [x] Graceful degradation: auto-fallback to `MonoTheme` when terminal lacks 256-color support
- [x] Test suite: AnsiStyle output, Theme contract (all 3 implementations), GridFrame borders, StatusBar rendering, gradient trail, flicker-free rendering, blocked flash, theme selection + auto-detect, multi-rover colors, MonoTheme backward compatibility
- [x] Coverage verification (maintain 95%+ branch coverage)

### V6a — Web Server + REST API + Static Frontend

**Scope:** Introduce `java -jar rover.jar --web [--port N]` mode. Embed a Javalin HTTP server that serves a vanilla HTML/CSS/JS single-page frontend and exposes REST endpoints for session creation, arena configuration, synchronous execution, and state retrieval. The frontend displays a Canvas-based grid, form-based configuration, and a "Run" button that submits config and renders the final state. No real-time animation yet — V6a validates the full stack end-to-end. All V0–V5 CLI modes remain unchanged.

- [x] Add Javalin + Jackson dependencies to `pom.xml`
- [x] `WebApp`: Javalin server lifecycle, static file serving from classpath, graceful shutdown hook
- [x] `SessionManager`: thread-safe session registry with `ConcurrentHashMap`; TTL reaper via `ScheduledExecutorService`; injectable `Clock` for testing
- [x] `Session`: per-session `Arena`, config, single-threaded executor, trail tracking via internal `RoverListener`, stats accumulation, `touch()` TTL tracking
- [x] DTOs (records): `ArenaConfig` (nullable width/height), `RoverSpecDto`, `PositionDto`, `SessionSnapshot`, `RoverStateDto`, `ViewportDto`, `RunStats`, `WebError`
- [x] `ViewportCalculator`: static helpers for bounded and auto-fit viewport computation (origin-inclusive, 2-cell padding, 10×10 min)
- [x] `ArenaConfigMapper`: DTO validation + mapping to `Environment` / `Arena`, with `ConfigValidationException` for partial/invalid configs
- [x] `ConfigValidationException`: carries `code` + message, thrown by mapper
- [x] `RoverController`: REST handlers for `POST /api/session`, `PUT /config`, `POST /run`, `GET /state`, `DELETE`, `POST /reset`
- [x] Error handling: `ConfigValidationException` → 400 with `WebError` envelope; missing session → 404; concurrent run → 409
- [x] Modify `App`: add `--web` and `--port` flags; delegate to `WebApp.main()` when `--web` is present
- [x] Frontend `index.html`: page shell with canvas region, viewport strip, legend block (3 rows), config form, run/reset buttons, status bar
- [x] Frontend `css/base.css` + `css/themes.css`: layout + CSS variables mirroring V5c themes
- [x] Frontend `js/api/client.js`: REST client with typed DTOs
- [x] Frontend `js/state/store.js`: observable store (subscribe/publish)
- [x] Frontend `js/ui/canvas.js`: grid rendering with dynamic cell sizing from viewport, axis labels, trails, rovers, obstacles (static; no animation in V6a)
- [x] Frontend `js/ui/legend.js`: dynamic legend block (symbols row, colors row, grid info row)
- [x] Frontend `js/ui/statusBar.js`: three-line status bar (state, last run, stats)
- [x] Frontend `js/ui/controls.js`: grid size inputs (allow empty for unbounded), obstacle list editor, rover form rows, conflict policy selector
- [x] Frontend `js/ui/toolbar.js`: run button, reset button, theme selector
- [x] Frontend `js/main.js`: wires API + store + UI modules
- [x] Test suite: `WebAppTest` (startup/shutdown), `SessionManagerTest` (lifecycle + TTL via injected clock), `SessionTest` (configure/run/snapshot/trails/stats), `ArenaConfigMapperTest` (bounded/unbounded/validation), `ViewportCalculatorTest` (bounded + auto-fit edge cases), `DtoJsonTest` (Jackson round-trip with nullable fields), `RoverControllerTest` (all REST endpoints + error paths), end-to-end `WebE2ETest` using Java `HttpClient`
- [x] Static asset test: Javalin serves `index.html` and JS/CSS with correct MIME types
- [x] Backward compatibility test: all V0–V5 CLI modes unchanged; no `--web` = old behavior
- [x] Manual smoke test: open browser, configure (both bounded and unbounded), run, verify final state and legend/viewport display
- [x] Coverage verification (maintain 95%+ branch coverage for Java code; frontend excluded from Jacoco)

**V6a patch — Bug fix: input cursor loss — Completed**

- [x] Refactor `controls.js` `renderControls()`: split into structural rebuild (ID/count changes) vs in-place value update (skip focused elements)
- [x] Track `lastRoverIds` array; only rebuild rover card DOM when IDs change
- [x] Track `lastObstacleCount`; only rebuild obstacle list when count changes
- [x] `updateRoverCardValues()`: iterate existing card DOM, patch values, skip `document.activeElement`
- [x] Manual smoke test: type rapidly in commands box — cursor stays in place; add/remove rover still works

**V6a patch — Feature: Continue Run (incremental execution) — Completed**

- [x] `Session.resetToStart()`: rebuilds Arena from stored config; resets trails + stats; does NOT require frontend to re-send config
- [x] `Session.run(Map<String, String>)`: accepts optional override commands; reuses existing Arena; appends trails; accumulates stats
- [x] `RoverController`: add `POST /api/session/{id}/reset` endpoint → calls `session.resetToStart()`, returns fresh snapshot; `POST /run` accepts `{"commands":{...}}` body
- [x] Frontend `toolbar.js` — Run button: always reconfigures (injects current positions from snapshot for Continue Run); does NOT clear commands after run
- [x] Frontend `toolbar.js` — Reset button: reconfigures with original form positions (rovers return to start)
- [x] Frontend `statusBar.js`: show cumulative stats across runs
- [x] Test suite: `SessionContinueRunTest` (run twice, verify second run starts from first run's endpoint; trails accumulate; stats accumulate; reset restores starting positions)
- [x] E2E test: `WebE2EContinueRunTest` (POST /run twice, GET /state after each, verify positions chain correctly; POST /reset, verify back to start)
- [x] Manual smoke test: configure → Run "MMR" → change commands to "MM" → Run → verify continues from last position → Reset → back at start

### V6b — Real-Time Event Streaming (SSE)

**Scope:** Add Server-Sent Events endpoint (`GET /api/session/{id}/events`) that streams `RoverEvent` data to connected browsers as each step executes. Introduce `SseRoverListener` that bridges the domain `RoverListener` interface to Javalin SSE clients. Frontend subscribes via native `EventSource` and animates the Canvas step-by-step as events arrive. Multi-rover sessions fan events out to all subscribers. Uses V5c theme colors (now as CSS variables) for per-rover coloring. Supports configurable animation delay via `delayMs` in the config.

- [ ] `SseRoverListener`: implements `RoverListener`, serializes events to JSON via Jackson, pushes to session subscribers
- [ ] `RoverEventDto`: JSON-friendly projection of `RoverEvent` with rover ID context
- [ ] `Session.subscribe()` / `unsubscribe()`: `CopyOnWriteArrayList<SseClient>` management
- [ ] `Session.run()`: attach `SseRoverListener` to each rover before execution; detach on completion
- [ ] `RoverController.subscribeEvents()`: opens SSE stream, calls `session.subscribe()`, sends initial state snapshot
- [ ] Graceful SSE cleanup: disconnect on client close, flush `complete` event on server shutdown
- [ ] Frontend `js/api/client.js`: add `subscribeEvents(sessionId, handler)` using `EventSource`
- [ ] Frontend `js/ui/canvas.js`: animated step-by-step rendering, trail accumulation, blocked flash
- [ ] Frontend `js/state/store.js`: event-driven state updates; current step, total steps, blocked status
- [ ] Per-rover CSS classes based on rover index (matches V5c `roverColor` palette)
- [ ] Delay control: `config.delayMs` passed through to `StepExecutor` on the server side
- [ ] Test suite: `SseRoverListenerTest` (event serialization, fan-out), `SseEndpointTest` (open stream, receive events, close), `SessionSubscriptionTest` (subscribe/unsubscribe lifecycle), fast-execution timing test (delay=0)
- [ ] Integration test: full REST + SSE flow — create session, configure, subscribe, run, verify events arrive in order
- [ ] Manual smoke test: watch rover animate in browser with matching V5c theme colors
- [ ] Coverage verification (maintain 95%+ branch coverage)

### V6c — Interactive Editing

**Scope:** Upgrade the frontend from form-based to point-and-click configuration. Click on the canvas to toggle obstacles. Click-to-add rovers with direction picker. Pause/resume/step controls during execution. Theme switcher (Modern / Minimal / Mono) mirroring V5c. Command input per rover with live validation. Reset button to clear state. Backend gains minimal additions for cooperative pause/resume.

- [ ] Frontend `js/ui/editor.js`: click handler for obstacle toggle, add-rover flow with direction picker
- [ ] Frontend `js/ui/toolbar.js`: pause/resume/step/reset/theme buttons
- [ ] Frontend `js/state/store.js`: undo/redo stack for edit operations
- [ ] Frontend command input validation: inline feedback for invalid characters
- [ ] Frontend hover highlight: show target cell on mouseover
- [ ] Backend: `Session.pause()` / `Session.resume()` / `Session.step()` — `volatile boolean paused` flag checked in step loop
- [ ] `RoverController`: `POST /api/session/{id}/pause`, `POST /api/session/{id}/resume`, `POST /api/session/{id}/step`
- [ ] Test suite: `SessionPauseResumeTest` (pause mid-run, resume continues, reset clears), REST endpoint tests for pause/resume/step
- [ ] Frontend manual smoke tests: all interactive controls work; undo/redo; theme switching
- [ ] Coverage verification

### V6d — Session Isolation (Multi-User Groundwork)

**Scope:** Harden session management so that the same server process can safely host many concurrent users, each with their own independent arena. This is not multi-user *gameplay* yet (that's V7) — it's the foundation: proven isolation, TTL cleanup, and concurrent stress tests. Add optional `--bind` flag to expose beyond localhost.

- [ ] `SessionManager` hardening: stress test with 20+ concurrent sessions; verify no state leakage
- [ ] TTL cleanup test with injectable `Clock`: sessions idle beyond TTL are removed; `touch()` extends lifetime
- [ ] Concurrent execution test: sessions A and B run simultaneously without interference
- [ ] Subscriber isolation test: events from session A only reach session A subscribers
- [ ] `WebApp`: add `--bind` flag (default `127.0.0.1`, override to `0.0.0.0` for LAN exposure)
- [ ] Rate limiting (basic): max N requests per session per second to prevent abuse
- [ ] Session limit: reject new sessions if server has >100 active (configurable)
- [ ] Test suite: `SessionIsolationTest`, `SessionManagerStressTest`, `RateLimitTest`, `BindFlagTest`
- [ ] Coverage verification
