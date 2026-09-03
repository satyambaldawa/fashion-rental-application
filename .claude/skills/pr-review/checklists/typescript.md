# TypeScript / React PR Review Checklist

Apply each relevant check to every changed `.ts`/`.tsx` file in the diff. Anchor every finding to a `file:line`.

---

## 1. No comments or JSDoc — make the code clean itself through naming

*Trigger:* Any comment (`//`, `/* */`) or JSDoc block (`/** */`) added or present in the diff, including "why" comments.

### What to check
- Flag it. This checklist treats comments as a smell to be replaced with better naming, not documented.
- Recommend the specific rename that would make the comment unnecessary: extract a well-named function/hook, rename a variable to state its purpose, or extract a named constant.

```tsx
// bad — comment explains what the code does
// check if the receipt is still within the grace period
if (Date.now() - receipt.returnedAt < 3600_000) { ... }

// good — the code says it itself
if (isWithinGracePeriod(receipt.returnedAt)) { ... }
```

If a comment captures a genuinely non-obvious constraint that no rename can express (e.g. a workaround for a specific browser/library bug), still flag it, but note the exception explicitly in the finding rather than silently skipping it — the developer decides whether to keep it.

---

## 2. SOLID principles (applied to components, hooks, and modules)

*Trigger:* Any new or modified component, hook, or module.

### Single Responsibility
- Does a component mix data-fetching, business logic, and rendering? A component that calls the API, transforms the response, and renders markup all in one body is a violation — split into a hook + a presentational component.

### Open/Closed
- See dedicated section 3 below — this is the check most likely to fire in practice.

### Liskov Substitution
- Rare in React/TS unless class hierarchies or shared prop-type contracts are involved. If a component implements a shared prop interface but narrows accepted values or throws on inputs the interface promises to accept, flag it.

### Interface Segregation
- Does a component/hook require props/arguments it doesn't use, just to satisfy a shared type? Split into smaller, role-specific prop types instead of one fat shape reused everywhere.

### Dependency Inversion
- Does a component hardcode a concrete API call (`axios.get(...)`, a specific `fetch` URL) instead of depending on an injected `src/api/*.ts` function or hook? Business logic should depend on the API module's exported function, not reimplement the call inline.

---

## 3. Open/Closed — avoid `if/else`/`switch` branching for behavior selection

*Trigger:* Any `if/else` chain or `switch` statement that selects different behavior/markup based on a type, enum, or status string — especially one that would need a new branch every time a new case is added.

### What to check
- Flag chains of 3+ `else if` branches (or any `switch` on a type/status) dispatching to different logic or rendering different markup.
- Recommend a lookup table (`Record<Status, Component>` or `Record<Status, () => JSX.Element>`), a strategy object, or polymorphic component composition — whichever fits the existing codebase's style.

```tsx
// bad — adding a new item type means editing this component
if (item.type === 'PACKAGE') {
  return <PackageCard item={item} />;
} else if (item.type === 'SINGLE') {
  return <SingleItemCard item={item} />;
} else if (item.type === 'ACCESSORY') {
  return <AccessoryCard item={item} />;
}

// good — adding a new item type means adding an entry, not editing this
const ITEM_CARD_BY_TYPE: Record<ItemType, React.FC<{ item: Item }>> = {
  PACKAGE: PackageCard,
  SINGLE: SingleItemCard,
  ACCESSORY: AccessoryCard,
};
const Card = ITEM_CARD_BY_TYPE[item.type];
return <Card item={item} />;
```

A single `if/else` for a genuine binary condition (not type-based dispatch) is not a violation — don't over-flag simple guard clauses or conditional rendering (`{isOwner && <Button />}`).

---

## 4. Composition over inheritance

*Trigger:* Any new `class extends` relationship, or hooks/components that duplicate logic instead of composing it.

### What to check
- Class inheritance is almost never appropriate in this codebase (function components + hooks). Flag any new class hierarchy and recommend composition instead.
- Is shared behavior duplicated across components instead of extracted into a custom hook (`useX`) or a wrapper component? Prefer composing hooks/components over copy-pasting logic.

---

## 5. Constants separated from core logic

*Trigger:* Any magic number, magic string, or repeated literal used in business logic.

### What to check
- Flag inline literals in conditionals, calculations, or comparisons (e.g. `if (retries > 3)`, `status === 'ACTIVE'` repeated across files, hardcoded route strings, hardcoded API paths).
- Recommend extracting to a dedicated constant, `enum`, or union type — kept in `src/types/` or a colocated `constants.ts`, not inline in the component/service using it.

```tsx
// bad
if (rental.status === 'ACTIVE') { ... }

// good
if (rental.status === RentalStatus.ACTIVE) { ... }
```

---

## 6. Modularity / component and function size

*Trigger:* Any component, hook, or function that grows substantially in this diff.

### What to check
- Does the component do more than one logical step (fetch, validate, transform, render, handle multiple unrelated events)? Split into smaller components/hooks.
- Does a component accumulate unrelated state/props over successive changes? That's a sign it's absorbing responsibilities that belong in a new hook or child component.

---

## 7. Immutability and null-safety

*Trigger:* Any new state, prop type, DTO, or function that can return "nothing."

### What to check
- Is state ever mutated directly (`array.push(...)`, `obj.field = x`) instead of replaced immutably (`setState([...array, x])`)?
- Does a new type use `any` or a non-null assertion (`!`) to sidestep a real null/undefined case, instead of narrowing the type or using optional chaining (`?.`)?
- Are nullable props/fields typed explicitly (`value: string | null`, `value?: string`) rather than left implicit or cast away?

---

## 8. Test coverage adequacy

*Trigger:* Every PR that changes component logic, hooks, or utility functions.

### What to check
- Not just "does a test file exist" — does the test suite actually exercise the behavior introduced or changed in this diff?
- For new/changed logic, check for: the happy path, at least one edge case (empty input, boundary value), and at least one error/failure path (e.g. failed API call, validation error).
- A diff that adds a new branch (see section 3) without a corresponding test case for that branch is a gap — call it out specifically, not just "add more tests."

---

## 9. Test hygiene

*Trigger:* Every changed test file.

### Object creation
- Are test fixtures built via a shared factory function or test-data builder, or is the same object/props shape reconstructed inline in every `it`/`test` block?
- Flag repeated identical object construction across multiple tests in the same file — extract a shared factory instead of duplicating it per test.

### Query and assertion hygiene
- Are queries using `screen.getByRole()` (or other user-facing queries) instead of `screen.getByTestId()`? Flag `getByTestId` unless there's no accessible role/label to query by.
- Flag assertions using unexplained literals where the reader can't tell what the number/string means or where it came from:

```tsx
// bad — what is 3?
expect(items).toHaveLength(3);

// good — intent is explicit
const EXPECTED_ITEM_COUNT = 3;
...
expect(items).toHaveLength(EXPECTED_ITEM_COUNT);
```

- This applies to expected values in assertions, not to trivial loop bounds or array indices where the meaning is already obvious from context.
