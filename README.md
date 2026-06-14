# Formatum Adhibe

`formatum-adhibe` is a Gradle plugin that bridges Checkstyle configuration into deterministic formatting steps for the Immaculate workflow system.

At a high level, the plugin:
- parses a Checkstyle XML file,
- plans deterministic formatter steps for supported rules,
- optionally applies regex-based hint rewrites for rules that need project-specific fixes.

## What it targets

The repository includes a primary compliance target at `libs/checkstyle/checkstyle.xml`.

Current tests and fixtures are built around that file, including integration tests that run `javaImmaculateApply` / `javaImmaculateCheck` against it.

## Features

- Deterministic mapping for core formatting checks such as:
  - newline at end of file,
  - trailing whitespace,
  - consecutive blank lines,
  - selected brace/blank-line patterns,
  - import order,
  - leading spaces to tabs.
- Hint-driven rewrites for rules that cannot be safely auto-fixed without explicit intent.
- `SAFE` and `AGGRESSIVE` fix modes.
- Validation toggles for unmatched hints and duplicate hint IDs.

## Applying the plugin in a consumer project

```groovy
plugins {
    id 'java'
    id 'com.communi.suggestu.formatum.adhibe' version '<version>'
}

immaculate {
    workflows.register('java') {
        java()
        checkstyle('checkstyle') {
            checkstyleConfig = file('checkstyle.xml')
            // Optional:
            // hintsFile = file('checkstyle-immaculate-hints.yaml')
            // fixMode = com.communi.suggestu.formatum.adhibe.checkstyle.hints.FixMode.SAFE
        }
    }
}
```

## Development quick start

### Prerequisites

- Gradle wrapper (`./gradlew`) from this repository.
- JDK defined by your build environment and `gradle.properties` (`java.version=21` currently).

If dependency resolution reports a newer minimum runtime in your environment, run Gradle with that newer JDK.

### Common commands

```bash
cd "/var/home/marchermans/IdeaProjects/FormatumAdhibe"
./gradlew test
```

```bash
cd "/var/home/marchermans/IdeaProjects/FormatumAdhibe"
./gradlew test --tests com.communi.suggestu.formatum.adhibe.plugin.FormatumAdhibeProjectPluginIntegrationTest
```

## Docs

- `docs/README.md` - docs index
- `docs/architecture.md` - parser/planner/formatter pipeline
- `docs/configuration.md` - Gradle DSL and step options
- `docs/hints.md` - hint file schema and matching behavior
- `docs/compliance-testing.md` - using `libs/checkstyle/checkstyle.xml` as a compliance target

