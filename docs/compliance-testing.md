# Compliance Testing

This project uses `libs/checkstyle/checkstyle.xml` as the primary compliance target.

## Why

- It is a realistic, non-trivial Checkstyle config.
- It validates parser, planner, and hint-resolution behavior together.
- It prevents regressions when rule mappings evolve.

## Existing coverage

Current tests in `src/test/java` include:
- parser tests for `libs/checkstyle/checkstyle.xml`,
- planner tests based on that same file,
- integration tests that execute plugin tasks in a temp Gradle project.

## Integration test pattern

A compliance integration test typically:
1. copies `libs/checkstyle/checkstyle.xml` into a temp project,
2. optionally writes a large hints YAML (especially for `MatchXpath` modules),
3. configures `immaculate { workflows.register('java') { ... } }`,
4. runs `javaImmaculateApply`, then `javaImmaculateCheck`,
5. asserts task success and key output formatting effects.

## Running compliance-oriented tests

```bash
cd "/var/home/marchermans/IdeaProjects/FormatumAdhibe"
./gradlew test --tests com.communi.suggestu.formatum.adhibe.plugin.FormatumAdhibePluginIntegrationTest
```

If your environment reports a minimum required JDK that is newer than your current runtime, switch Gradle/JAVA_HOME to that JDK and rerun.

## Optional report generation

Rule classification outputs are generated in:
- `build/reports/checkstyle-rule-matrix.md`
- `build/reports/checkstyle-unsupported.txt`

Use these as snapshots during development and PR review.

