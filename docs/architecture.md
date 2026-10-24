# Architecture

`formatum-adhibe` is a Gradle plugin layered on top of Immaculate.

## Pipeline

1. **Plugin wiring**
   - `FormatumAdhibePlugin` applies `ImmaculatePlugin`.
   - It attaches `checkstyle(...)` as a workflow action via `WorkflowCheckstyleAction`.

2. **Checkstyle parsing**
   - `CheckstyleConfigParser` parses the XML into a `CheckstyleModuleSpec` tree.
   - Module path values are stable and include indexes (for example `Checker[0]/TreeWalker[0]/MatchXpath[7]`).

3. **Deterministic planning**
   - `DeterministicCheckstylePlanner` maps known module patterns to generated formatter step specs.
   - Example: `ImportOrder` -> import sorter step spec.

4. **Formatter creation**
   - `CheckstyleDeterministicStep` instantiates concrete formatter steps from planned specs.
   - Formatters are applied in deterministic order.

5. **Optional hint phase**
   - If `hintsFile` exists, `CheckstyleHintsParser` reads YAML hints.
   - `HintResolver` matches hints to modules by `modulePath`, `moduleName`, and optional `messageContains`.
   - `HintRegexStepFactory` compiles regex replacements and appends them to the formatter chain.

## Default behavior

`WorkflowCheckstyleAction` sets defaults:
- `checkstyleConfig = file('checkstyle.xml')`
- `stepNamePrefix = 'checkstyle'`
- `fixMode = SAFE`
- `failOnUnmatchedHints = true`
- `failOnHintConflicts = true`

## Design intent

- Keep deterministic formatting simple and safe.
- Require explicit hints for potentially semantic rewrites.
- Make compliance measurable against real project Checkstyle configs.

## Java source regions

`RegionFinder` uses the running JDK's public `JavacTask.parse()` API to discover
Java syntax. It does not perform attribution (`analyze()`), compile source, or
run annotation processors, so application dependencies do not need to resolve.
No Checkstyle or external Java-parser dependency is required.

AST source positions supply statement and declaration ranges and identify
lambda, anonymous-class, and ordinary block ownership. A small lexical pass
locates paired delimiters omitted from the public AST, ignoring comments,
character/string literals, and text blocks. Unicode escapes are translated for
lexing while offsets continue to refer to the original source.

These ranges become a nested `Region` tree with exclusive end offsets.
Crossing ranges are rejected rather than producing a misleading tree.
`Region` retains the existing indentation and continuation policy; AST depth
alone does not determine indentation. Formatting edits the original text,
not javac's printed representation.

Member and statement fragments are parsed inside temporary wrappers whose
regions and offsets are excluded from the result. Invalid syntax fails with
javac diagnostics. Both retained `ParseMode` values now require valid syntax;
`DEFAULT` no longer permits the old scanner's partial interpretation.

Language support follows the **JDK running Gradle**, not the project's compilation
toolchain. The formatter requires a full JDK including `jdk.compiler`; run
Gradle on a JDK that understands the source being formatted. Parsing enables
that JDK's preview syntax, but does not enable preview compilation for the
consumer project. This parser does not launch a separate toolchain JVM.
