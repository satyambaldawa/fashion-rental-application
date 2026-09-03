# Java PR Review Checklist

Apply each relevant check to every changed Java file in the diff. Anchor every finding to a `file:line`.

---

## 1. No comments or Javadoc — make the code clean itself through naming

*Trigger:* Any comment (`//`, `/* */`) or Javadoc block (`/** */`) added or present in the diff, including "why" comments.

### What to check
- Flag it. This checklist treats comments as a smell to be replaced with better naming, not documented.
- Recommend the specific rename that would make the comment unnecessary: extract a well-named private method, rename a variable to state its purpose, or extract a named constant.

```java
// bad — comment explains what the code does
// check if the vehicle is currently charging
if (vehicle.getStatus() == 3) { ... }

// good — the code says it itself
if (vehicle.isCharging()) { ... }
```

If a comment captures a genuinely non-obvious constraint that no rename can express (e.g. a workaround for a specific third-party bug), still flag it, but note the exception explicitly in the finding rather than silently skipping it — the developer decides whether to keep it.

---

## 2. SOLID principles

*Trigger:* Any new or modified class/interface.

### Single Responsibility
- Does the class have more than one reason to change? A class mixing persistence, business rules, and formatting/logging concerns is a violation.

### Open/Closed
- See dedicated section 3 below — this is the check most likely to fire in practice.

### Liskov Substitution
- Does a subclass override a method in a way that narrows preconditions, widens postconditions, or throws where the base type does not promise to? Callers relying on the base type's contract should not break when handed a subtype.

### Interface Segregation
- Does an interface force implementers to provide methods they don't need (e.g. throwing `UnsupportedOperationException`)? Split into smaller, role-specific interfaces instead.

### Dependency Inversion
- Does a high-level class depend on a concrete low-level class instead of an abstraction (e.g. a service `new`-ing a concrete repository instead of depending on an injected interface)?

---

## 3. Open/Closed — avoid `if/else`/`switch` branching for behavior selection

*Trigger:* Any `if/else` chain or `switch` statement that selects different behavior based on a type, enum, or status code — especially one that would need a new branch every time a new case is added.

### What to check
- Flag chains of 3+ `else if` branches (or any `switch` on type/enum) dispatching to different logic.
- Recommend polymorphism (each case becomes a subtype implementing a common method), the Strategy pattern (inject the behavior), or a `Map<Key, Handler>` lookup — whichever fits the existing codebase's style.

```java
// bad — adding a new event type means editing this method
if (event.getType() == EventType.IGNITION_ON) {
    handleIgnitionOn(event);
} else if (event.getType() == EventType.IGNITION_OFF) {
    handleIgnitionOff(event);
} else if (event.getType() == EventType.GEOFENCE_ENTRY) {
    handleGeofenceEntry(event);
}

// good — adding a new event type means adding a new handler, not editing this
Map<EventType, EventHandler> handlers; // wired via DI or a static map
handlers.get(event.getType()).handle(event);
```

A single `if/else` for a genuine binary condition (not type-based dispatch) is not a violation — don't over-flag simple guard clauses.

---

## 4. Composition over inheritance

*Trigger:* Any new `extends` relationship, or an existing inheritance hierarchy touched by the diff.

### What to check
- Is inheritance used purely to reuse code (not because the subtype genuinely "is a" supertype)? Prefer composition — hold a reference to the reusable behavior and delegate to it.
- Is the hierarchy more than 2 levels deep? Deep hierarchies are a sign responsibilities should be decomposed into collaborators instead.

---

## 5. Constants separated from core logic

*Trigger:* Any magic number, magic string, or repeated literal used in business logic.

### What to check
- Flag inline literals in conditionals, calculations, or comparisons (e.g. `if (rpm == 16384)`, `if (retries > 3)`).
- Recommend extracting to a dedicated constants holder — a `Constants` class, an `enum`, or a `static final` field grouped with other constants — kept separate from the class containing the logic that uses it, not colocated inline.

```java
// bad
if (vehicle.getEngineRpm() == 16384) { ... }

// good
if (vehicle.getEngineRpm() == VehicleConstants.DEFAULT_ENGINE_RPM_SENTINEL) { ... }
```

---

## 6. Modularity / class and method size

*Trigger:* Any class or method that grows substantially in this diff.

### What to check
- Does the method do more than one logical step? Long methods mixing validation, transformation, and I/O should be split.
- Does the class accumulate unrelated fields/methods over successive changes? That's a sign it's absorbing responsibilities that belong in a new collaborator.

---

## 7. Immutability and null-safety

*Trigger:* Any new field, DTO, or method that can return "nothing."

### What to check
- Are fields that never change after construction marked `final`? Are new DTOs/value objects immutable (all-final fields, no setters) rather than mutable beans?
- Does a method return `null` to signal absence where `Optional<T>` would make the contract explicit at the call site?
- Are nullable fields/parameters documented by type (`Optional`, `@Nullable`) rather than left ambiguous?

---

## 8. Test coverage adequacy

*Trigger:* Every PR that changes production logic.

### What to check
- Not just "does a test file exist" — does the test suite actually exercise the behavior introduced or changed in this diff?
- For new/changed logic, check for: the happy path, at least one edge case (empty input, boundary value), and at least one error/failure path.
- A diff that adds a new branch (see section 3) without a corresponding test case for that branch is a gap — call it out specifically, not just "add more tests."

---

## 9. Test hygiene

*Trigger:* Every changed test file.

### Object creation
- Are test fixtures built via a shared builder, factory method, or object-mother helper, or is the same object reconstructed inline in every `@Test` method?
- Flag repeated identical object construction across multiple tests in the same class — extract a shared builder/factory instead of duplicating constructor calls per test.

### No unexplained raw literals in assertions
- Flag assertions using unexplained literals where the reader can't tell what the number/string means or where it came from:

```java
// bad — what is 11?
assertEquals(11, response.getCount());

// good — intent is explicit
private static final int EXPECTED_COUNT = 11;
...
assertEquals(EXPECTED_COUNT, response.getCount());
```

- This applies to expected values in assertions, not to trivial loop bounds or array indices where the meaning is already obvious from context.
