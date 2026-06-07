# Configuration

The plugin exposes a `checkstyle(name) { ... }` workflow action inside Immaculate workflows.

## Basic example

```groovy
immaculate {
    workflows.register('java') {
        java()
        checkstyle('checkstyle') {
            checkstyleConfig = file('checkstyle.xml')
        }
    }
}
```

## Full example

```groovy
immaculate {
    workflows.register('java') {
        java()
        checkstyle('checkstyle') {
            checkstyleConfig = file('checkstyle.xml')
            stepNamePrefix = 'checkstyle'
            hintsFile = file('checkstyle-immaculate-hints.yaml')
            fixMode = com.communi.suggestu.formatum.adhibe.checkstyle.hints.FixMode.AGGRESSIVE
            failOnUnmatchedHints = true
            failOnHintConflicts = true
        }
    }
}
```

## Properties

- `checkstyleConfig` (`RegularFileProperty`, required)
  - Path to Checkstyle XML input.
  - Default: project `checkstyle.xml`.

- `stepNamePrefix` (`Property<String>`, optional)
  - Prefix used for generated step names.
  - Default: `checkstyle`.

- `hintsFile` (`RegularFileProperty`, optional)
  - YAML hints for rules not covered by deterministic mappings.
  - Ignored when absent.

- `fixMode` (`Property<FixMode>`, required with default)
  - `SAFE` (default): only hints marked `SAFE` apply.
  - `AGGRESSIVE`: applies both `SAFE` and `AGGRESSIVE` hints.

- `failOnUnmatchedHints` (`Property<Boolean>`, default `true`)
  - Fails when a hint does not match any module in parsed Checkstyle config.

- `failOnHintConflicts` (`Property<Boolean>`, default `true`)
  - Fails on duplicate non-blank hint IDs.

## Task usage

With a `java` workflow configured, Immaculate tasks typically include:
- `javaImmaculateApply`
- `javaImmaculateCheck`

Run them with Gradle:

```bash
cd "/var/home/marchermans/IdeaProjects/FormatumAdhibe"
./gradlew javaImmaculateApply
```

```bash
cd "/var/home/marchermans/IdeaProjects/FormatumAdhibe"
./gradlew javaImmaculateCheck
```

