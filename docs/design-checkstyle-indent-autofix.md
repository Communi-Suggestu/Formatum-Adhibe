# Design Proposal: Auto-fixable Checkstyle `IndentationCheck` violations via FormatumAdhibe

**Status:** DRAFT — requires reconciliation with the Checkstyle-mechanism findings before implementation.
**Scope:** C&B `api` module only (`mod.chiselsandbits.*`). All targets below are drawn from *real* source in `/home/marchermans/IdeaProjects/Chisels-and-Bits/api/src/main/java/mod/chiselsandbits/`.
**No files edited.** This is a planning document.

---

## 0. How Formatum's indentation model works (grounding)

`CheckstyleIndentationStep.apply()` (`src/main/java/.../formatting/steps/CheckstyleIndentationStep.java`) rewrites **every** non-empty line to:

```java
int effectiveIndent = rootRegion.regionDepthAtStartOfLine(i);   // a TAB COUNT
lines.set(i, "\t".repeat(Math.max(0, effectiveIndent)) + trimmed);
```

`regionDepthAtStartOfLine(lineIndex)` (`Region.java`) walks the **parental region chain** for that line and accumulates depth from exactly four signals:

| Signal (in `Region.regionDepthAtStartOfLine`) | Depth delta | Trigger condition |
|---|---|---|
| Parameter block | **+2** | a `(` … `)` param list spanning lines (`isParameterBlock`), with closing-operand guards |
| Multi-line broken assignment continuation | **+2** (or +1) | `=` present, `isStatement`, multi-line, has a spanning parameter child (`isMultiLineBrokenAssignmentStatement`) |
| Multi-line method-statement continuation | **+2** | `isStatement` + multiple parameter children + multi-line (`isMultiLineMethodStatement`) |
| Code block | **+1** | `isCodeBlock` and not an isolated start/end operand |

`RegionFinder` (`src/main/java/.../utils/RegionFinder.java`) builds the region tree from the javac AST via a `TreePathScanner.Builder`. Crucial facts for this design:

- **`Kind` enum** = `ROOT, BLOCK, PARAMETER, ARRAY, STATEMENT, CONTROL, ANNOTATION, ENUM_CONSTANT` (line 90–92).
- `scan()` (lines 211–227) adds spans for: `EXPRESSION_STATEMENT/RETURN/THROW/...` → `STATEMENT`; `IF/FOR/WHILE/SWITCH/SWITCH_EXPRESSION/TRY/CATCH/SYNCHRONIZED` → `CONTROL`. **`CASE` / `LABEL_DEF` are NOT handled** — they fall through `default -> {}`, producing *no* distinct span.
- `addBody()` (lines 229–235) creates a `BLOCK` spanning from the `{` to its matching `}`. So method bodies, class bodies, and **switch bodies** are all `BLOCK`s contributing **+1** to every line strictly inside them.
- Depth is computed purely from tree structure; there is **no per-config-property adjustment**. Formatum emits *raw tabs* (`"\t".repeat(...)`); it does **not** consume tab-width or basicOffset when emitting — the tab→column mapping is assumed downstream.

**Tab/column convention used throughout this doc (ASSUMPTION — §7):** each indentation level = **1 tab = 4 columns**, matching the task's stated facts ("3 tabs = col 12", "2 tabs = col 8"). Under this convention: shape-1 labels go 3→2 tabs; shape-2 continuuations go 2→3 tabs. `basicOffset` is assumed **4** (C&B sets `lineWrappingIndentation=8`, giving the clean inheritance continuation of `8/4 = 2` tabs) and `caseIndent=0`.

---

## 1. Shape 1 — Switch-case labels (IPositionMutator L113–115; VectorUtils L37/43/49/55)

### Current behavior
- **VectorUtils** is a *classic* switch statement with split label/colon:
  ```java
  		switch (axis) {          // depth 2 (classBlock + methodBlock); its own switch body BLOCK excluded on the opening line
  			case X              // current depth 3 → 3 tabs (col 12)   ← VIOLATION
  			: return new Vec3(
  			vector3d.x(),       // body handled elsewhere
  		}
  ```
  The `case X` label sits **inside** the switch's `{ }` BLOCK, so it accrues an **+1 code-block level** that pushes it to depth 3. Checkstyle (with `caseIndent=0`) wants case labels aligned with the *switch statement's own indentation* = depth 2 → **2 tabs (col 8)**.
- **IPositionMutator** is a *switch expression* using arrow labels (`case X -> ...;`), returned from a method. Same over-indentation: `case X -> ...` at depth 3 vs wanted 2.
- Root cause: Formatum has no notion of "case label"; it treats them as generic body lines inside the switch BLOCK, and **never consumes `getCaseIndent()`** (the property is declared `@Input`, wired in `CheckstyleDeterministicStep` line 387, but unused in `apply()`).

### Required region / metadata change in RegionFinder
Emit a signal that distinguishes *case-label lines* from ordinary body lines so their depth can exclude the switch BODY block.

1. **Add `Kind.CASE_LABEL`** and handle it in `scan()`: add `case CASE -> add(start(tree), end(tree), Kind.CASE_LABEL);`. This gives each case label a neutral span (depth contribution 0, like `CONTROL`).
2. **Mark switch body blocks.** In `addBody`/the second `switch` (line 221–223) where `SWITCH/SWITCH_EXPRESSION` already call `addBody`, set a flag on the produced block: `block.switchBody = true`. Thread this into `Region` as a new boolean field **`isSwitchBody`** (added to both constructors).
3. **Detect label text** inside the depth method via `getContentLine(lineIndex).trim()` prefix `"case"` (see guard below).

### Required change in `CheckstyleIndentationStep.apply()` / `regionDepthAtStartOfLine`
- In `Region.regionDepthAtStartOfLine`, add a branch **before** the code-block contribution: if the current line is a case label AND its innermost enclosing BLOCK is a switch-body block, **skip that block's +1** and instead use the depth of the enclosing SWITCH statement. Concretely:

```java
// regionDepthAtStartOfLine — insert near the code-block branch (Region.java ~196)
String l = getContentLine(lineIndex).trim();
boolean isCaseLabel = l.startsWith("case") && nextTokenIsNotIdentifier(l); // guard vs method names
if (isCaseLabel && region.isSwitchBody() && !previousOrNextIsStatementStart(lineIndex)) {
    // do NOT add the +1 code-block depth; this label takes the SWITCH keyword's own level.
    if (debugMode == RegionFinder.DebugMode.RegionDepthTracking)
        depthReasons.add("Case label excluded switch-body-block depth (+caseOffset below)");
} else if (region.isCodeBlock() && !region.isIsolatedStartOrEndOperand(lineIndex)) {
    depth++; // existing code block +1
}
```

- **Consume `getCaseIndent()`** to re-introduce the configured offset. Pass it into the depth computation (or apply as a post-step in `apply()`):
  ```java
  int caseOffset = getCaseIndent().getOrElse(0);   // C&B sets this to 0
  effectiveIndent += basicOffsetUnits * caseOffset; // basicOffsetUnits == 1 tab here (basicOffset==tabWidth)
  ```
  With `caseIndent=0`, labels land at the switch keyword level (depth 2). If a future config sets `caseIndent=N`, labels shift deeper by N tabs — matching Checkstyle semantics.

### Edge cases / risks
- **Arrow vs colon style.** IPositionMutator uses arrow (`case X ->`); VectorUtils uses classic split label (`case X` + newline + `:`). Checkstyle *may* indent these differently (arrow-case bodies often align with the switch column; colon-case labels typically go +basicOffset under default configs — but C&B forces `caseIndent=0`). **Both are asserted to target col 8 in this task**, so treat them uniformly, but **confirm** arrow/colon parity (§7).
- **The `:` continuation line** (`: return new Vec3(`) must NOT be treated as a case label — it is the statement start and should keep its depth. Guard on text: only lines whose *first* non-space token is exactly `case`.
- **Nested switches / switch inside lambda / case bodies with their own blocks.** The "innermost switch-body block" rule can misfire; verify the nearest enclosing CONTROL is a SWITCH, not an IF/FOR/WHILE.
- **Label text false positives:** a method named `cases()` or variable `caseSensitive` — guard requires the token boundary after `case` (whitespace, `:`, or `->`).

---

## 2. Shape 2 — Method-def continuation of a wrapped expression-statement (StateEntryStorage L422–426)

### Current behavior
```java
	public String toString() {
		return "SimpleStateEntryStorage{" +     // statement base: depth 2 (classBlock + methodBlock)
		"palette = " + palette +                // current depth 2 → 2 tabs (col 8)   ← VIOLATION
		", data = " + data +
		'}';
	}
```
The wrapped string-concatenation `return` statement spans multiple lines but is **neither** an assignment (no top-level `=` — the `=` lives *inside* the literal `"palette = "`), **nor** a method call with parameter blocks (`Vec3(...)`-style). So both existing continuation rules are inert:
- `isMultiLineBrokenAssignmentStatement()` returns false because there is no spanning PARAMETER child.
- `isMultiLineMethodStatement()` returns false because there are no parameter children at all.

Result: continuation lines receive only the base depth (2 tabs). Checkstyle wants a wrapped *expression-statement* indented **one basicOffset deeper** than its opening keyword → depth 3 → **3 tabs (col 12)**. Formatum produces "wrong output" because it has no generic expression-continuation rule.

### Required region / metadata change in RegionFinder
Introduce a new continuation category: a multi-line **expression statement that is not an assignment and not a parameter-bearing method call**.

- In `scan()`, `RETURN`/`EXPRESSION_STATEMENT` are already added as `Kind.STATEMENT`. Add a discriminator on the STATEMENT span, computed at build time in `region()`:
  ```java
  // new flag on Region: isWrappedExpressionStatementContinuation(lineIndex)
  boolean stmt = kind == Kind.STATEMENT && !isParameterBlock(kind);
  ```
- Extend `Region` with a method (mirroring the existing `isMultiLineMethodStatement()`/`isMultiLineBrokenAssignmentStatement()` pattern):
  ```java
  private boolean isWrappedExpressionStatement() {
      return isStatement() && isMultiLine()
          && !isMultiLineBrokenAssignmentStatement()      // excludes assignment + array-init continuations
          && !hasParameterChildrenSpanningLines();         // excludes method-call continuations
  }
  ```
  **Detection must be AST-based, not textual** — the `=` inside `"palette = "` must not count as an assignment operator (the current code already guards this via the parameter-child check, but a purely text-based approach would regress).

### Required change in `CheckstyleIndentationStep.apply()` / `regionDepthAtStartOfLine`
- Add a branch after the method-statement continuation branch (`Region.java ~182`):
  ```java
  // Generic wrapped-expression-statement continuation -> +basicOffset (one tab)
  else if (region.isWrappedExpressionStatement()
           && (previous == null || !previous.isParameterBlock())
           && region.inRegionLineOffset(lineIndex) > 0      // only continuation lines, not the opening line
           && !region.isStart(lineIndex)) {
      depth++;                                               // +1 tab = basicOffset units here
      if (debugMode == RegionFinder.DebugMode.RegionDepthTracking)
          depthReasons.add("Wrapped expression-statement continuation depth increase");
  }
  ```
  This yields depth 3 for L423–425 and leaves the opening `return` line at depth 2.

### Edge cases / risks
- **Double counting with existing rules.** Must exclude lines already handled by the broken-assignment (+2) or method-statement (+2) branches — hence the `else if` ordering and the explicit exclusions above. A wrapped statement that *also* has a spanning parameter block must take the method-statement path, not this one.
- **`=` inside string literals / comments.** AST-based detection avoids the naive `indexOf("=")` trap; but confirm no test feeds an assignment whose RHS contains `=` in a way that trips existing guards (there is a known TODO at Region.java:71).
- **Chained method calls** (`foo()\n  .bar()`) — each `.method` continuation currently relies on the parameter-block rule or falls through; ensure this new branch does not over-indent line continuations of fluent chains that should align to the statement column. Verify against existing `FormattingMechanRegressionTest`.
- **Opening vs closing operand lines.** The guard `inRegionLineOffset > 0` plus `!isStart` restricts to true continuation lines; ensure a two-line statement where line 2 is the last line still gets +1 (it should — only the *opening* keyword line stays at base).

---

## 3. Shape 3 — Compact single-line constructor (CollisionType L27)

### Current behavior
```java
public enum CollisionType              // enum body BLOCK (+1)
{
	...                                   // constants / fields
	CollisionType(final Predicate<BlockState> isValidFor, ..., boolean canBeEmptyWithJustFluids){this.isValidFor = isValidFor;  // depth 1 (enumBodyBlock only)
		this.exampleState = exampleState;   // depth 2 (enumBodyBlock + ctorBodyBlock)   ← CORRECT per task
		this.canBeEmptyWithJustFluids = canBeEmptyWithJustFluids;
	}                                     // closing brace depth 1
}
```
The constructor opens `{` and immediately continues with `this.isValidFor = isValidFor;` **on the same line** (compact/inline body). The real body statements L28–30 already indent correctly at depth 2. Checkstyle's ChildrenAlignment reports:

> `'ctor def' child level 4 expected 8`

i.e. the constructor definition's **first child** (`{` / inline first statement) is measured at "level 4" but Checkstyle expects "8". The violation is fundamentally a **brace-formatting / line-structure** issue, not a plain indentation amount: the first body statement should sit on its *own* line indented one basicOffset into the body (depth 3 → col 12), matching L28–30's depth.

### Why Formatum produces no change today
`CheckstyleIndentationStep` only **reindents** lines; it never splits `{` from its body, never inserts blank/separating lines, and never changes brace placement (that is the domain of `LeftCurlyStep`, `NeedBracesStep`, `EnsureTrailingNewlineStep`). For L27:
- The ctor name line computes depth 1 → Formatum writes `\tCollisionType(...){this...` — it preserves the inline tail verbatim (`trimmed` after `{`), so nothing changes.
- Because the violation is "first child inline at ctor level instead of on a new deeper line", **reindenting alone cannot clear it**.

### Required change (design-level, not pure reindent)
Reconciling shape 3 requires one of:
1. **Coordinate with the brace-formatting step:** have `LeftCurlyStep`/brace handling split `{` from the first body statement onto separate lines so the normal indentation machinery yields the expected child depth — *then* IndentationStep's existing depth-2 → (statement now at depth 3) agrees automatically. This is the cleanest path but crosses step boundaries and must be gated to `INDENTATION` only when brace style also requires it.
2. **Dedicated "inline ctor body" rule:** detect a constructor whose `{` is immediately followed by a statement on the same line, and (a) reindent that inline tail to depth = ctor-base + basicOffset *and* (b) require/emit an inserted newline before it — which is outside IndentationStep's scope.

Either way, the fix **cannot be expressed as a `regionDepthAtStartOfLine` delta alone**. The design must surface this boundary and decide whether to (i) extend an existing brace step, or (ii) add a small pre-processing pass in the INDENTATION flow that normalizes compact ctors before reindent.

### Edge cases / risks
- **Allowable inline ctors.** Confirm whether C&B's Checkstyle config permits single-line constructor bodies at all; if allowed, there is no violation to fix and this shape is out of scope. If disallowed (as the message implies), a brace step must split them.
- **Enum constructors are implicitly private** and javac positions the synthetic type oddly (`isEnumConstant` handling in RegionFinder:252–255) — verify the ctor's region tree does not collide with enum-constant detection.
- **Interaction with `needBraces`.** If `NeedBracesStep` also runs, ordering matters; a compact `{this...}` may already be normalized or flagged by that step first.

---

## 4. Wiring: consuming the currently-dead properties

`CheckstyleIndentationStep` declares four indentation config properties as `@Input` but **only consumes two** (`getLineWrappingIndentation()` for `continuationTabs`, and `getBasicOffset()` via that ratio). The others are dead in `apply()`:

| Property | Declared | Currently used? | Where it MUST be consumed |
|---|---|---|---|
| `getBasicOffset()` | line 22 | ✅ (via `continuationTabs`) — but never applied as a depth unit directly | Already feeds shape-1/shape-2 tab math; keep. |
| `getCaseIndent()` | line 25 | ❌ | Shape 1: switch-base + `basicOffsetUnits * caseIndent` for case labels (§1). |
| `getThrowsIndent()` | line 28 | ❌ | Wrapped method-declaration `throws T1, T2, …` continuation should be `throws-indent` units beyond the signature. Not in the three shapes but must be wired to avoid silent mis-indent if such signatures exist in C&B `api`. |
| `getArrayInitIndent()` | line 31 | ❌ | `ARRAY` kind exists (`isArrayInitializer`) but **never contributes depth**. Wrapped array initializers `{ a, b, … }` currently get no init offset. Wire `arrayInitIndent` into the code-block/continuation math. |
| `getLineWrappingIndentation()` | line 34 | ✅ (only for extends/implements inheritance tabs) | Shape-agnostic; keep. |

**How to wire without destabilizing existing output:** add small, well-guarded branches in `regionDepthAtStartOfLine` (mirroring the existing `isMultiLineMethodStatement` style), each gated on the specific structural signal so unrelated lines are untouched — exactly as proposed in §1 and §2 for `caseIndent`. Keep everything behind a "was this branch actually triggered" check so regression tests see no change for files that don't exercise these shapes.

### Latent bug: default mismatches at CheckstyleDeterministicStep :387 / :390
The INDENTATION wiring (lines 386–390) uses `.orElse(4)` for **every** property:
```java
step.getCaseIndent().set(... module.property("caseIndent")....orElse(4));   // line 387 — should default to CHECKSTYLE's caseIndent default, NOT 4
step.getLineWrappingIndentation().set(... module.property("lineWrappingIndentation")....orElse(4)); // line 390 — C&B wants 8
```
Two problems:
1. **Default value is hardcoded to 4** regardless of the property's real Checkstyle default. If a C&B module omits `caseIndent`, Formatum defaults it to **4** (which, if ever consumed as a multiplier, would push labels +4 levels — catastrophic); the intended default is **0**. If it omits `lineWrappingIndentation`, Formatum defaults to **4** but C&B expects **8**, silently halving inheritance-list continuation depth.
2. **Inconsistent with Checkstyle's own defaults.** The values should come from a single canonical source (either Checkstyle library defaults or the shared C&B config), not four independent `orElse(4)` literals scattered across the switch.

**Proposed fix:** replace the per-property `.orElse(4)` with module-level property resolution against one map of canonical defaults, and confirm the correct default set (§7). Until shape-1 wiring consumes `getCaseIndent()`, this bug is dormant but will surface immediately once §1 lands.

---

## 5. Acceptance targets (authoritative — from Checkstyle's own messages)

| Shape | File(s) | Current | Target | Tab delta |
|---|---|---|---|---|
| 1 Switch-case labels | IPositionMutator L113–115; VectorUtils L37/43/49/55 | 3 tabs (col 12) | col 8 (`caseIndent=0`) | −1 tab |
| 2 Wrapped expr-statement continuation | StateEntryStorage L422–426 | 2 tabs (col 8) | col 12 (+basicOffset) | +1 tab |
| 3 Inline ctor first child | CollisionType L27 | inline at ctor level ("level 4") | "expected 8" via brace split | structural (see §3) |

---

## 6. Suggested implementation order (for after reconciliation)
1. **Shape 1** — add `Kind.CASE_LABEL`, `isSwitchBody` flag, case-label detection + `getCaseIndent()` consumption. Lowest blast-radius; closes the most violations.
2. **Shape 2** — add `isWrappedExpressionStatement` discriminator + generic continuation +1. Watch double-count interaction with existing branches.
3. **Shape 3** — coordinate with brace/line step (preferred) or dedicated inline-ctor pass; confirm allowability first (§7).
4. **Wiring** — dead-properties consumption + fix the :387/:390 defaults in one canonical spot.
5. Add regression tests mirroring `RegionFinderTests` / `FormattingMechanRegressionTest`, keyed on the C&B `api` fixtures.

---

## 7. ASSUMPTIONS TO CONFIRM (must be reconciled before implementation)

1. **"level" semantics in messages.** Is `"child level 4 expected 8"` measured in *tabs*, in *basicOffset multiples*, or in *columns*? **This drives the entire tab math for shape 3** and must be confirmed by running Checkstyle directly on `CollisionType.java` (and reading the exact emitted column/tab). Do not assume until observed.
2. **Inline ctor bodies allowed?** Does C&B's Checkstyle config permit compact single-line constructor bodies? If *allowed*, there is no violation and shape 3 drops out of scope; if *disallowed* (as the message implies), a brace/line step must split them — confirm which.
3. **Exact expected depth for L27.** What tab count does Formatum need to emit so `'ctor def' child level ... expected ...` clears? Only obtainable from a real Checkstyle run, not derivable here.
4. **basicOffset value in C&B config.** The task states `caseIndent=0` and `lineWrappingIndentation=8` but does not state `basicOffset`. Analysis assumes **4** (consistent with `continuationTabs = 8/4 = 2`). Confirm; it changes every tab→column conversion.
5. **Tab width.** Assumed **1 tab = 4 columns** to match "3 tabs = col 12" / "2 tabs = col 8". Formatum emits raw tabs and does not read `tabWidth`; confirm the C&B/Checkstyle config's `tabWidth` and whether Checkstyle runs against Formatum's *tabs* or expanded spaces.
6. **Arrow vs colon switch parity.** IPositionMutator (arrow, `case X ->`) vs VectorUtils (colon, split label) — do they need identical depth treatment, or does Checkstyle differ between them? Task groups them at col 8; confirm uniformity holds for both styles.
7. **Scope enforcement.** Confirm how Formatum scopes INDENTATION steps per source module (`modulesByPath` in `CheckstyleDeterministicStep`) so case/throws/array settings apply to the C&B **`api`** module only and cannot leak into other modules' files.
8. **String-literal `=` false positives.** Confirm AST-based continuation detection is sufficient — specifically that a wrapped statement containing `=` *inside* a string literal (e.g. `"palette = "`) is not mis-classified as an assignment. The existing guard relies on a spanning parameter child; verify no C&B `api` file has a multi-line assignment whose only `=`-containing region lacks such a child and would now be mishandled by the new branch.
9. **Defaults canonicalization.** Decide whether defaults should mirror Checkstyle's *library* defaults or the shared C&B config values before changing the `.orElse(4)` literals (§4).
