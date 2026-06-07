# Hints

Hints are regex rewrite rules used when deterministic planning is not enough for a Checkstyle rule.

## File format

Example `checkstyle-immaculate-hints.yaml`:

```yaml
hints:
  - id: assert-to-comment
    modulePath: Checker[0]/TreeWalker[0]/MatchXpath[7]
    find: "\\bassert\\b"
    replace: "// assert"
    multiline: false
    dotall: false
    mode: AGGRESSIVE
```

## Fields

- `id` (optional, recommended)
  - Logical identifier for conflict checks.

- `modulePath` (optional)
  - Exact module path from parsed Checkstyle tree.
  - Most precise match strategy.

- `moduleName` (optional)
  - Module name (for example `RegexpSingleline`, `MatchXpath`).

- `messageContains` (optional)
  - Additional filter against module message text.

- `find` (required)
  - Java regex pattern.

- `replace` (required)
  - Replacement string.

- `multiline` (optional, default `false`)
  - Enables `Pattern.MULTILINE`.

- `dotall` (optional, default `false`)
  - Enables `Pattern.DOTALL`.

- `mode` (required)
  - `SAFE` or `AGGRESSIVE`.

## Matching behavior

A hint matches a module when:
- `modulePath` matches exactly (if provided),
- `moduleName` matches exactly (if provided),
- and `messageContains` is found in the module message (if provided).

If no hint selector (`modulePath` or `moduleName`) is provided, the hint does not match.

## Validation behavior

- `failOnUnmatchedHints = true`: fails when configured hints match no module.
- `failOnHintConflicts = true`: fails on duplicate non-empty hint IDs.

## Large compliance hint files

For broad compliance tests against large configs (such as `libs/checkstyle/checkstyle.xml`), create path-based placeholders for each targeted module path. This keeps tests strict (`failOnUnmatchedHints=true`) while avoiding accidental broad matches.

