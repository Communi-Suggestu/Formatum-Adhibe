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

