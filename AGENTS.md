## Android

- Before changing navigation, workout behavior, or data boundaries, read the [MVP overview](docs/mvp.md).
- For Android tests, builds, lint, and device checks, follow the [test-layer and verification rules](docs/android-development.md#run-checks).
- For Room schema changes, add a migration, keep the schema history in `app/schemas/`, and [test the APK update](docs/android-development.md#install-or-update-fitty) with data from the previous build.
- When opening or updating a PR, add `android-smoke` if its changed paths require [Android Smoke](.github/workflows/android-smoke.yml).

## Project conventions

- Read the [issue tracker guide](docs/agents/issue-tracker.md) before reading or updating tickets.
- Use the [triage label guide](docs/agents/triage-labels.md) when assigning or interpreting triage labels.
- Follow the [domain guide](docs/agents/domain.md) when using domain terms or reviewing architecture decisions.
