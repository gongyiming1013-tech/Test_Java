# AI Coding Collaboration Protocol

## 1. Coding Standards & Design Principles

### SOLID Principles

All designs must be decoupled, interface-based, and extensible. Do NOT over-engineer.

- **S — Single Responsibility:** One class, one job. Keep each class and method concise and focused.
- **O — Open/Closed:** Add new behavior via new classes or modules, not by modifying existing ones.
- **L — Liskov Substitution:** Subclasses must be drop-in replacements for their parent type.
- **I — Interface Segregation:** Keep interfaces small and focused. Clients should not depend on methods they do not use.
- **D — Dependency Inversion:** Depend on abstractions (interfaces/protocols), inject concrete implementations.

### OOD Best Practices

- **Favor Composition over Inheritance.** Compose behavior from small, focused objects rather than deep inheritance hierarchies.
- **Use appropriate Design Patterns** based on the domain. Prefer simple, well-known patterns:
  - **Strategy** — swappable algorithms
  - **Observer** — event notification
  - **Factory Method** — conditional object creation
  - **Singleton** — global unique instance
  - **Iterator** — custom traversal
  - **Command / State** — when the domain calls for them
- **Avoid complex patterns** (Builder, Abstract Factory, nested Decorators) unless the problem explicitly requires them.

### Test-Driven Development (TDD)

Tests are not an afterthought — they are a core design tool. Always follow this order:

1. **Define the interface first:** Establish abstract classes or interfaces that describe the contract.
2. **Write tests against the contract:** Develop test cases based on the expected behavior before any implementation exists.
3. **Implement to pass the tests:** Write the minimum concrete code needed to satisfy the test suite.

This ensures that every component is testable by design, contracts are explicit and well-understood, and regressions are caught immediately.

### Domain-Driven Naming

Use descriptive, ubiquitous language for all entities, variables, and methods.

| Element               | Convention         | Example                          |
|-----------------------|--------------------|----------------------------------|
| Classes / Interfaces  | PascalCase         | `LRUCache`, `FileParser`         |
| Methods / Functions   | snake_case or camelCase (per language convention) | `get_value` / `getValue` |
| Variables             | snake_case or camelCase (per language convention) | `max_size` / `maxSize`   |
| Constants             | UPPER_SNAKE_CASE   | `MAX_CAPACITY`, `DEFAULT_TIMEOUT`|
| Private members       | Language-appropriate access modifier or naming convention | `_cache` (Python), `private` (Java/C++) |

### Clean Code

- **Keep functions small** and focused on a single responsibility.
- **Prefer clarity over cleverness.** Code is read far more often than it is written.
- **Type safety:** Use the language's type system (type hints, generics, static types) in all method signatures wherever possible.
- **Encapsulation:** Expose the minimum public API necessary. All internal state and helpers must be private.

### Class Structure

Organize every class in a consistent order:

1. Class-level documentation (docstring / doc comment)
2. Constructor / initializer
3. Public methods
4. Private helpers

### Comments & Documentation

- Every class and public method must have a doc comment.
- Inline comments explain **why**, not **what**.
- Do not over-comment obvious code.

### Error Handling

- Define a base custom exception per domain, with specific subclasses.
- Use **guard clauses** — put boundary checks at the top of methods.
- Only catch exceptions where recovery is possible. Do not swallow errors silently.

---

## 2. Thinking & Planning Workflow (Mandatory)

Before writing any implementation code, follow this high-level thinking process:

### Phase A: High-Level Conceptualization

- **Data Structures:** Identify the core data structures and their relationships.
- **Core Entities:** Define the primary classes and their key responsibilities.
- **Functionality:** Outline the must-have capabilities to meet the requirements.
- **Test Dimensions:** Plan the key dimensions for testing (e.g., core functionality, edge cases, error handling, concurrency, performance). No detailed test cases yet — just identify *what* needs to be tested and from which angles.

Document the results of this phase in `DEVELOPMENT_PLAN.md`.

### Phase B: Execution Roadmap

Define a versioned roadmap in `DEVELOPMENT_PLAN.md`:

- **V0 (MVP):** The minimum viable functional version.
- **V1+ (Enrichment):** Incremental enhancements (e.g., concurrency, advanced constraints, persistence).

### Phase C: Contract & TDD Setup

1. **Define Stubs:** Create abstract classes / interfaces and method signatures based on the plan.
2. **Write Test Cases:** Develop comprehensive unit tests based on the API contract.
3. **User Review:** STOP and ask the user to review the Design & Test suite before proceeding to implementation.

---

## 3. Implementation & Iteration Rules

- **Step-by-Step Execution:** Follow the `DEVELOPMENT_PLAN.md` sequentially. Do not skip steps.
- **Continuous Verification:** Run the test suite and check branch coverage after every meaningful feature implementation.
- **Iterative Refinement:** Based on test results or user feedback, iterate on the code until it meets the quality bar.

---

## 4. Quality & Commit Strategy

### Test Coverage

- Maintain a minimum of **95% branch coverage**.
- Every public class and method must have corresponding test cases.
- Use the language's standard test framework (e.g., `pytest`, `JUnit`, `Jest`, `go test`).

### Edge Cases

- Explicitly handle null/nil inputs, boundary conditions, and concurrency race conditions.

### Final Review

- Once tests pass, propose a **Commit Strategy** using [Conventional Commits](https://www.conventionalcommits.org/) and wait for user approval before checking in the code.

---

## General Rules

- Prefer clarity over cleverness.
- Keep each class and method concise and focused.
- When proposing a solution, briefly explain the design rationale.
