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

### V0 (MVP) — Current
- Infinite plane, single rover, three actions (L, R, M)
- Full TDD with 95%+ branch coverage

### V1+ (Future)
- Grid boundaries, multiple rovers, interactive mode, undo/redo

## Implementation Order

1. `Direction` enum (leaf — no deps)
2. `Position` record (depends on Direction)
3. `RoverState` record (pure data carrier)
4. `Action` interface + TurnLeft/TurnRight/MoveForward
5. `InvalidActionException` + `ActionParser`
6. `Rover`
7. `App` integration
8. Coverage verification
